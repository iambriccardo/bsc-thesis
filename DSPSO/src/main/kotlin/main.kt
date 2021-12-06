import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaFutureAction
import org.apache.spark.api.java.JavaSparkContext
import scala.Tuple2
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.isEmpty()) exitProcess(0)

    val spark = SparkConf()
        .setAppName("DSPSO")
        .set("spark.scheduler.mode", "FAIR") // We allow multiple jobs to be executed in a round robin fashion.
        .set("spark.driver.bindAddress", "127.0.0.1")

    val sc = JavaSparkContext(spark)

    val executeSync = args[0] == "sync"
    val begin = System.currentTimeMillis()
    if (executeSync) {
        println("Starting SYNCHRONOUS PSO...")
        synchronousPSO(sc)
    } else {
        println("Starting ASYNCHRONOUS PSO...")
        asynchronousPSO(sc)
    }
    val end = System.currentTimeMillis()

    println("Elapsed time in milliseconds: ${end - begin}")
}

fun synchronousPSO(sc: JavaSparkContext) {
    // The global position accumulator is registered.
    val globalBestPositionAccumulator = PositionAccumulator(MutablePosition.BestPosition())
    sc.sc().register(globalBestPositionAccumulator, "GlobalBestAccumulator")

    val iterations = 10
    var isFirstRound = true
    var inputParticles: List<Particle>
    var movedParticles: List<Particle> = emptyList()
    var bestPosition: Tuple2<Position<Double>?, Double?>? = null

    repeat((0 until iterations).count()) {
        println("Iteration $it started")
        inputParticles = if (isFirstRound) randomParticlesOfDouble(50, 2) else movedParticles

        // The particles are evaluated and the best positions are computed.
        val evaluatedParticles = sc
            .parallelize(inputParticles)
            .map(FitnessEvaluation(FitnessFunction.Sphere(), globalBestPositionAccumulator))
            .collect()

        // The best global position is derived via a merge of all the executor specific accumulators.
        // The value is then broadcast to all the executors of the cluster, as a read only variable.
        val bestGlobalPositionBroadcast = sc.broadcast(globalBestPositionAccumulator.value())

        // The particles' velocity and position are updated according to the best positions computed in the
        // previous step.
        movedParticles = sc
            .parallelize(evaluatedParticles)
            .map(PositionEvaluation(bestGlobalPositionBroadcast))
            .collect()

        bestPosition = bestGlobalPositionBroadcast.value
        println("Best global so far: ${bestGlobalPositionBroadcast.value}")

        isFirstRound = false
        println("Iteration $it ended")
    }

    println("Best position: ${bestPosition!!._1} ${bestPosition!!._2}")
}

fun asynchronousPSO(sc: JavaSparkContext) = runBlocking {
    val numberOfParticles = 50
    val numberOfIterations = 10
    val numberOfEvaluations = numberOfParticles * numberOfIterations
    val particles = randomParticlesOfDouble(numberOfParticles, 2)

    val particlesChannel = Channel<Particle>(capacity = Channel.UNLIMITED).apply { particles.forEach { send(it) } }
    val futuresChannel = Channel<JavaFutureAction<List<Particle>>>(capacity = Channel.UNLIMITED)

    val stateActor = stateActor()

    val producer = launch {
        for (particle in particlesChannel) {
            // We send the computation to the cluster.
            val future = sc.parallelize(listOf(particle))
                .repartition(1)
                .map(AsyncFitnessEvaluation(FitnessFunction.Sphere()))
                .collectAsync()

            // We send the future to the channel which contains all the computations
            // we expect to be finished in the future.
            futuresChannel.send(future)
        }
    }

    val consumer = launch {
        repeat(numberOfEvaluations) {
            val future = futuresChannel.receive()

            val particle = withContext(Dispatchers.IO) {
                future.get().first()
            }

            // We increment the number of evaluated particles.
            stateActor.mutate { state ->
                state.incrementCounter()
            }

            // We update the best global position.
            stateActor.mutate { state ->
                state.mutateBestGlobalPosition(Tuple2(particle.position, particle.error))
            }

            // We get a snapshot of the state.
            val state = stateActor.snapshot()

            println(state.bestGlobalPosition.position())
            println(state.bestGlobalPosition.error())

            // We update the particle's position and velocity.
            particle.updateVelocity(state.bestGlobalPosition.position())
            particle.updatePosition()

            // We send again the particle for further evaluation.
            // TODO: send only if the channel contains less elements that the remaining number of iterations.
            particlesChannel.send(particle)
        }

        // We cancel the particles channel.
        particlesChannel.cancel()

        // We perform the cleanup of the futures after the specific number of iterations is reached.
        while (!futuresChannel.isEmpty) {
            val future = futuresChannel.receive()
            future.cancel(true)
        }
    }

    producer.join()
    consumer.join()

    val state = stateActor.snapshot()
    println("Best position: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

    // We close the state actor in order to let the block finish.
    stateActor.close()
}