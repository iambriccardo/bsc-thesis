package pso

import AsyncFitnessEvaluation
import FitnessEvaluation
import FitnessFunction
import MutablePosition
import Particle
import Position
import PositionAccumulator
import PositionEvaluation
import SuperRDD
import aggregate
import incrementCounter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mutate
import mutateBestGlobalPosition
import org.apache.spark.api.java.JavaFutureAction
import org.apache.spark.api.java.JavaSparkContext
import randomParticlesOfDouble
import scala.Tuple2
import snapshot
import stateActor
import stopAggregation
import superRDDAggregator
import updatePosition
import updateVelocity
import util.Configuration

object PSO {

    /**
     * Synchronous version of the Spark Distributed PSO algorithm.
     */
    fun sync(config: Configuration, sc: JavaSparkContext): Tuple2<Position<Double>?, Double?> {
        // The global position accumulator is registered.
        val globalBestPositionAccumulator = PositionAccumulator(MutablePosition.BestPosition())
        sc.sc().register(globalBestPositionAccumulator, "GlobalBestAccumulator")

        var isFirstRound = true
        var inputParticles: List<Particle>
        var movedParticles: List<Particle> = emptyList()
        var bestPosition: Tuple2<Position<Double>?, Double?>? = null

        repeat(config.iterations.toInt()) {
            println("Iteration $it started")
            inputParticles = if (isFirstRound)
                randomParticlesOfDouble(config.particles.toInt(), config.dimensionality.toInt())
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
            movedParticles = if (config.distributedPosEval) {
                sc.parallelize(evaluatedParticles)
                    .map(PositionEvaluation(bestGlobalPositionBroadcast))
                    .collect()
            } else {
                evaluatedParticles.map { particle ->
                    PositionEvaluation(bestGlobalPositionBroadcast).call(particle)
                }
            }

            bestPosition = bestGlobalPositionBroadcast.value
            println("Best global until now: ${bestGlobalPositionBroadcast.value}")

            isFirstRound = false
            println("Iteration $it ended")
        }

        println("Best position: ${bestPosition!!._1} ${bestPosition!!._2}")

        return bestPosition!!
    }

    /**
     * Asynchronous version of the Spark Distributed PSO algorithm.
     */
    fun async(config: Configuration, sc: JavaSparkContext) = runBlocking {
        val superRDDSize = config.superRDDSize.toInt()
        if (superRDDSize > config.particles.toInt())
            throw RuntimeException("The superRDD size must be <= than the number of particles.")

        val fillingParticles =
            if (config.particles.toInt() % superRDDSize == 0) 0
            else (superRDDSize - config.particles.toInt() % superRDDSize)
        val numberOfParticles = config.particles.toInt() + fillingParticles

        // The random particles are initialized.
        val particles = randomParticlesOfDouble(
            numberOfParticles, // We want to avoid non-full super rdds.
            config.dimensionality.toInt()
        )
        println("Updated number of particles: $numberOfParticles")

        // Channel containing all the superRDDs which are going to be executed against the cluster.
        val superRDDChannel = Channel<SuperRDD>(capacity = Channel.UNLIMITED)
        val aggregator = superRDDAggregator(superRDDSize, superRDDChannel)
        particles.forEach { aggregator.aggregate(it) }

        // Channel containing the futures connected to the particles remote evaluation.
        val futuresChannel = Channel<JavaFutureAction<List<Particle>>>(capacity = Channel.UNLIMITED)

        // Shared state actor which simplifies shared state among coroutines.
        val stateActor = stateActor()

        val producer = launch {
            for (superRDD in superRDDChannel) {
                println("Parallelizing superRDD of size ${superRDD.particles.size}...")

                // We send the computation to the cluster.
                val future = sc.parallelize(superRDD.particles)
                    .map(AsyncFitnessEvaluation(FitnessFunction.Sphere()))
                    .collectAsync()

                // We send the future to the channel which contains all the computations
                // we expect to be finished in the future.
                futuresChannel.send(future)
            }
        }

        val consumer = launch {
            // We iterate for an equivalent number of partitions.
            repeat(numberOfParticles * config.iterations.toInt() / superRDDSize) {
                withContext(Dispatchers.IO) {
                    futuresChannel.receive().get()
                }.forEach { particle ->
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
                    aggregator.aggregate(particle)
                }
            }

            // We stop the aggregation of particles.
            aggregator.stopAggregation()

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

            // We cancel the super rdd channel.
            superRDDChannel.cancel()
        }

        producer.join()
        consumer.join()

        val state = stateActor.snapshot()
        println("Best position: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

        // We close the state actor in order to let the block finish.
        stateActor.close()

        return@runBlocking Tuple2(state.bestGlobalPosition.position(), state.bestGlobalPosition.error())
    }
}
