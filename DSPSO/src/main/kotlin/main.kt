import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaFutureAction
import org.apache.spark.api.java.JavaSparkContext
import scala.Tuple2
import java.io.File
import kotlin.system.measureTimeMillis

fun main(args: Array<String>) {
    val config = args.asConfiguration()

    val spark = SparkConf()
        //.setMaster("local[*]")
        .setAppName("DSPSO")
        .set("spark.scheduler.mode", "FAIR") // We allow multiple jobs to be executed in a round robin fashion.
        .set("spark.kubernetes.driver.annotation.sidecar.istio.io/inject", "false")
        .set("spark.kubernetes.executor.annotation.sidecar.istio.io/inject", "false")

    val sc = JavaSparkContext(spark)

    val time = measureTimeMillis {
        if (config.isSynchronous) {
            println("Starting SYNCHRONOUS PSO...")
            synchronousPSO(config, sc)
        } else {
            println("Starting ASYNCHRONOUS PSO...")
            asynchronousPSO(config, sc)
        }
    }
    println("Elapsed time minutes: ${time / 1000.0 / 60.0}")
    println("Elapsed time seconds: ${time / 1000.0}")
    println("Elapsed time milliseconds: $time")
}

fun synchronousPSO(config: Configuration, sc: JavaSparkContext) {
    // The global position accumulator is registered.
    val globalBestPositionAccumulator = PositionAccumulator(MutablePosition.BestPosition())
    sc.sc().register(globalBestPositionAccumulator, "GlobalBestAccumulator")

    var isFirstRound = true
    var inputParticles: List<Particle>
    var movedParticles: List<Particle> = emptyList()
    var bestPosition: Tuple2<Position<Double>?, Double?>? = null

    repeat((0 until config.iterations).count()) {
        println("Iteration $it started")
        inputParticles = if (isFirstRound)
            randomParticlesOfDouble(config.particles, config.dimensionality)
        else movedParticles

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
        println("Best global until now: ${bestGlobalPositionBroadcast.value}")

        isFirstRound = false
        println("Iteration $it ended")
    }

    println("Best position: ${bestPosition!!._1} ${bestPosition!!._2}")

    println("Writing result to ${config.outputPath}...")
    val output = File(config.outputPath)
    output.createNewFile()
    output.writeText("Best position: ${bestPosition!!._1} ${bestPosition!!._2}")
    println("Results written to ${config.outputPath}")
}

fun asynchronousPSO(config: Configuration, sc: JavaSparkContext) = runBlocking {
    // The random particles are initialized.
    val particles = randomParticlesOfDouble(config.particles, config.dimensionality)

    // Channel containing the particles that need to be sent for evaluation.
    val particlesChannel = Channel<Particle>(capacity = Channel.UNLIMITED)
        .apply { particles.forEach { send(it) } }
    // Channel containing the futures connected to the particles remote evaluation.
    val futuresChannel = Channel<JavaFutureAction<List<Particle>>>(capacity = Channel.UNLIMITED)

    // Shared state actor which simplifies shared state among coroutines.
    val stateActor = stateActor()

    val producer = launch {
        for (particle in particlesChannel) {
            // We send the computation to the cluster.
            val future = sc.parallelize(listOf(particle))
                .map(AsyncFitnessEvaluation(FitnessFunction.Sphere()))
                .collectAsync()

            // We send the future to the channel which contains all the computations
            // we expect to be finished in the future.
            futuresChannel.send(future)
        }
    }

    val consumer = launch {
        repeat(config.iterations * config.particles) {
            val future = futuresChannel.receive()

            val particle = withContext(Dispatchers.IO) {
                future.get().first()
            }
            println("Received evaluated particle at $it")

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
            println("Best global until now: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

            // We update the particle's position and velocity.
            particle.updateVelocity(state.bestGlobalPosition.position())
            particle.updatePosition()

            // We send again the particle for further evaluation.
            // TODO: send only if the channel contains less elements that the remaining number of iterations.
            particlesChannel.send(particle)
        }

        // We perform the cleanup of the futures after the specific number of iterations is reached.
        // Due to the async behavior of this code it may happen that some futures finish while we are
        // deleting the preceding ones.
        // e.g.
        // future_1, future_2, future_3
        // We can future_1 and future_2 while also future_3 is running, if it takes a small amount of time
        // it will be finished before we cancel it. For our use case this is not a big deal, because the computation
        // will be simply lost and nothing more.
        println("Cleaning up remaining jobs...")
        while (!futuresChannel.isEmpty) {
            val future = futuresChannel.receive()
            future.cancel(true)
        }
        println("Remaining jobs cleaned up")

        // We cancel the futures channel.
        futuresChannel.cancel()

        // We cancel the particles channel.
        particlesChannel.cancel()
    }

    producer.join()
    consumer.join()

    val state = stateActor.snapshot()
    println("Best position: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

    println("Writing result to ${config.outputPath}...")
    val output = File(config.outputPath)
    output.createNewFile()
    output.writeText("Best position: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")
    println("Results written to ${config.outputPath}")

    // We close the state actor in order to let the block finish.
    stateActor.close()
}