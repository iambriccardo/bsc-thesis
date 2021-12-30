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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
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
import updateBestPersonalPosition
import updatePosition
import updateVelocity
import util.Configuration

object PSO {

    /**
     * Normal version of the standard PSO algorithm.
     */
    fun normal(config: Configuration): Tuple2<Position<Double>?, Double?> {
        // Base algorithm parameters.
        val iterations = config.iterations.toInt()
        val particles = config.particles.toInt()
        val dimensionality = config.dimensionality.toInt()

        // Best global position.
        val bestGlobalPosition = MutablePosition.BestPosition()

        var inputParticles: List<Particle> = randomParticlesOfDouble(particles, dimensionality)
        repeat(iterations) {
            println("Iteration $it started")

            // The particles are evaluated and the best positions are computed.
            inputParticles = inputParticles
                .map { particle ->
                    particle.apply {
                        particle.error = FitnessFunction.Sphere().delayedEvaluation(
                            config.fitnessEvalDelay.toLong(),
                            particle.position
                        )
                        particle.updateBestPersonalPosition()
                        bestGlobalPosition.mutate(particle.position, particle.error!!)
                    }
                }
                .map { particle ->
                    particle.apply {
                        updateVelocity(bestGlobalPosition.position())
                        updatePosition()
                    }
                }

            println("Best global position until now: ${bestGlobalPosition.position()}  ${bestGlobalPosition.error()}")
        }

        println("Best global position: ${bestGlobalPosition.position()}  ${bestGlobalPosition.error()}")

        return Tuple2(bestGlobalPosition.position(), bestGlobalPosition.error())
    }

    /**
     * Synchronous version of the Spark Distributed PSO algorithm.
     */
    fun sync(config: Configuration, sc: JavaSparkContext): Tuple2<Position<Double>?, Double?> {
        // Base algorithm parameters.
        val iterations = config.iterations.toInt()
        val particles = config.particles.toInt()
        val dimensionality = config.dimensionality.toInt()

        // Spark accumulator for accumulating the best global position for each partition.
        val bestGlobalPositionAccumulator = PositionAccumulator(MutablePosition.BestPosition())
        sc.sc().register(bestGlobalPositionAccumulator, "BestGlobalPositionAccumulator")

        // Best global position.
        var bestGlobalPosition: Tuple2<Position<Double>?, Double?> = Tuple2(null, null)

        var inputParticles: List<Particle> = randomParticlesOfDouble(particles, dimensionality)
        repeat(iterations) {
            println(
                "Iteration $it started with " +
                        "${if (config.distributedPosEval) "distributed" else "centralized"} collection."
            )

            // The particles are evaluated and the best positions are computed.
            inputParticles = sc
                .parallelize(inputParticles)
                .map(
                    FitnessEvaluation(
                        FitnessFunction.Sphere(),
                        bestGlobalPositionAccumulator,
                        config.fitnessEvalDelay.toLong()
                    )
                )
                .collect()

            // The best global position is derived via a merge of all the executor specific accumulators.
            bestGlobalPosition = bestGlobalPositionAccumulator.value()

            // The particles' velocity and position are updated according to the best positions computed in the
            // previous step.
            inputParticles = if (config.distributedPosEval) {
                // The best global position is broadcast to all the nodes in the cluster.
                val bestGlobalPositionBroadcast = sc.broadcast(bestGlobalPosition)

                // We evaluate each particle's position on the cluster by reading the best
                // global position from the broadcast variable.
                sc.parallelize(inputParticles)
                    .map(PositionEvaluation(bestGlobalPositionBroadcast))
                    .collect()
            } else {
                // We evaluate each particle's position locally.
                inputParticles.map { particle ->
                    particle.updateVelocity(bestGlobalPosition._1)
                    particle.updatePosition()

                    particle
                }
            }

            println("Best global position until now: $bestGlobalPosition")
        }

        println("Best global position: $bestGlobalPosition")

        return bestGlobalPosition
    }

    /**
     * Asynchronous version of the Spark Distributed PSO algorithm.
     */
    fun async(config: Configuration, sc: JavaSparkContext) = runBlocking {
        // Base algorithm parameters.
        val iterations = config.iterations.toInt()
        val baseParticles = config.particles.toInt()
        val dimensionality = config.dimensionality.toInt()
        val superRDDSize = config.superRDDSize.toInt()
        if (superRDDSize < 1 || superRDDSize > baseParticles)
            throw RuntimeException("The superRDD size must be > 0 or <= than the number of particles.")
        // We compute the number of filling particles in order to obtain full super rdds with the aim of
        // maximizing each super rdd content.
        //
        // e.g. If we have the super rdd of size 8, and we decide to use 10 particles, we are always going to
        // end up with two super rdds, one with 8 particles and one with 2 particles, which will be a waste of resources
        // especially if the super rdd size if computed to exploit as much as possible the underlying cluster. Therefore,
        // the algorithm will automatically add 6 particles so that we are able to create two full super rdds of size 8,
        // with a total of 16 particles. This allows also for a more precise conversion of iterations, meaning that if we have
        // 16 particles and 10 iterations we know that the equivalent number of particle evaluations in the sync pso will be 160,
        // therefore in the async algorithm we are going to evaluate 160 particles, however if the super rdd size n is > 1,
        // then 160/n iterations will be needed to consume the same number of particles because each iteration will consume n particles.
        val fillingParticles =
            if (baseParticles % superRDDSize == 0) 0
            else (superRDDSize - (baseParticles % superRDDSize))
        val particles = baseParticles + fillingParticles

        // The random particles are initialized.
        val inputParticles = randomParticlesOfDouble(particles, dimensionality)
        println("Updated number of particles: $particles")

        // Channel containing all the superRDDs which are going to be executed against the cluster.
        val superRDDChannel = Channel<SuperRDD>(capacity = Channel.UNLIMITED)
        // Aggregator which will manage all the aggregation of incoming particles into super rdds.
        val aggregator = superRDDAggregator(superRDDSize, superRDDChannel)
        inputParticles.forEach { aggregator.aggregate(it) }
        // Channel containing the futures connected to the particles remote evaluation.
        val futuresChannel = Channel<JavaFutureAction<List<Particle>>>(capacity = Channel.UNLIMITED)

        // Shared state actor which contains the share state accessible in a thread safe way by all
        // the coroutines.
        val stateActor = stateActor()

        val producer = launch {
            println("Producer: running on ${Thread.currentThread().name}")

            for (superRDD in superRDDChannel) {
                println("Parallelizing superRDD of size ${superRDD.particles.size}...")

                // We send the computation to the cluster.
                val future = sc.parallelize(superRDD.particles)
                    .map(AsyncFitnessEvaluation(FitnessFunction.Sphere(), config.fitnessEvalDelay.toLong()))
                    .collectAsync()

                // We send the future to the channel which contains all the computations
                // we expect to be finished in the future.
                futuresChannel.send(future)
            }
        }

        val consumer = launch {
            println("Consumer: running on ${Thread.currentThread().name}")

            // We iterate for an equivalent number of partitions to the number of theoretical
            // particle evaluations done if this algorithm was synchronous.
            val waitingList = mutableListOf<Deferred<Unit>>()
            repeat((particles * iterations) / superRDDSize) {
                // We create a coroutine for each separate super rdd we want to get and run it on a
                // background pool of threads optimized for IO operations.
                val job = async(Dispatchers.IO) {
                    futuresChannel.receive().get().forEach { particle ->
                        println("Received evaluated particle at $it on thread ${Thread.currentThread().name}")

                        // We update the best global position.
                        stateActor.mutate { state ->
                            state.mutateBestGlobalPosition(Tuple2(particle.position, particle.error))
                        }

                        // We get a snapshot of the state.
                        val state = stateActor.snapshot()
                        println("Best global position until now: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

                        // We update the particle's position and velocity.
                        particle.updateVelocity(state.bestGlobalPosition.position())
                        particle.updatePosition()

                        // We send again the particle for aggregation into a new super rdd.
                        aggregator.aggregate(particle)
                    }
                }

                // We add the asynchronous collection to the list of all the jobs currently
                // waiting for the future to finish.
                waitingList.add(job)
            }

            // We wait for all the jobs to finish.
            waitingList.awaitAll()

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

        // We are waiting for the producer and consumer to finish their work.
        producer.join()
        consumer.join()

        // We get a snapshot of the state in order to get the best global position.
        val state = stateActor.snapshot()
        println("Best global position: ${state.bestGlobalPosition.position()} ${state.bestGlobalPosition.error()}")

        // We close the state actor in order to let the block finish.
        stateActor.close()

        return@runBlocking Tuple2(state.bestGlobalPosition.position(), state.bestGlobalPosition.error())
    }
}
