import org.apache.spark.api.java.function.Function
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.util.AccumulatorV2
import scala.Tuple2

class PositionAccumulator(
    private val position: MutablePosition<PosPlacementMatrix, Double>
) : AccumulatorV2<Tuple2<PosPlacementMatrix?, Double?>, Tuple2<PosPlacementMatrix?, Double?>>() {
    override fun isZero(): Boolean {
        return position.position() == null && position.error() == null
    }

    override fun copy(): AccumulatorV2<Tuple2<PosPlacementMatrix?, Double?>, Tuple2<PosPlacementMatrix?, Double?>> {
        // TODO: maybe .copy() is needed for the position.
        return PositionAccumulator(
            MutablePosition.BestPosition(position.position(), position.error())
        )
    }

    override fun reset() {
        position.reset()
    }

    override fun add(value: Tuple2<PosPlacementMatrix?, Double?>) {
        if (value._1 != null && value._2 != null)
            position.mutate(value._1!!, value._2!!)
    }

    override fun merge(other: AccumulatorV2<Tuple2<PosPlacementMatrix?, Double?>, Tuple2<PosPlacementMatrix?, Double?>>) {
        // When we merge two position accumulators we just perform an addition with the current accumulator.
        add(other.value())
    }

    override fun value(): Tuple2<PosPlacementMatrix?, Double?> {
        return Tuple2(position.position(), position.error())
    }
}

data class SuperRDD(val particles: List<Particle>)

class FitnessEvaluation(
    private val fitnessFunction: FitnessFunction<FitnessFunction.PlacementCost.PlacementInput, Double>,
    private val runtimesMatrixBroadcast: Broadcast<MutableList<DoubleArray>>,
    private val bestGlobalPositionAccumulator: PositionAccumulator,
    private val delay: Long = 0
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The fitness function is evaluated with the current particle's position.
        particle.error = fitnessFunction.delayedEvaluation(
            delay,
            FitnessFunction.PlacementCost.PlacementInput(
                particle.position,
                runtimesMatrixBroadcast.value
            )
        )

        // The personal best position is saved.
        particle.updateBestPersonalPosition()

        // The shared accumulator is updated by the executor.
        bestGlobalPositionAccumulator.add(Tuple2(particle.position, particle.error))

        return particle
    }
}

class AsyncFitnessEvaluation(
    private val fitnessFunction: FitnessFunction<FitnessFunction.PlacementCost.PlacementInput, Double>,
    private val runtimesMatrixBroadcast: Broadcast<MutableList<DoubleArray>>,
    private val delay: Long = 0
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The fitness function is evaluated with the current particle's position.
        particle.error = fitnessFunction.delayedEvaluation(
            delay,
            FitnessFunction.PlacementCost.PlacementInput(
                particle.position,
                runtimesMatrixBroadcast.value
            )
        )

        // The personal best position is saved.
        particle.updateBestPersonalPosition()

        return particle
    }
}

class PositionEvaluation(
    private val bestGlobalPositionBroadcast: Broadcast<Tuple2<PosPlacementMatrix?, Double?>>
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The best global position is read from the broadcast variable copy on the executor.
        val bestGlobalPosition = bestGlobalPositionBroadcast.value._1

        // The particle's velocity is updated.
        particle.updateVelocity(bestGlobalPosition)

        // The particle's position is updated.
        particle.updatePosition()

        return particle
    }
}