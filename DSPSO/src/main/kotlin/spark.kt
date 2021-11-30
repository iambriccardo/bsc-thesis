import org.apache.spark.api.java.function.Function
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.util.AccumulatorV2
import scala.Tuple2

// TODO: https://github.com/nohkwangsun/spark-using-kotlin/blob/master/gradle-project/src/main/kotlin/rdd/K010_Accumulators2.kt
// TODO: https://spark.apache.org/docs/latest/rdd-programming-guide.html

class PositionAccumulator(
    private val position: MutablePosition<Double>,
    private val resettable: Boolean = true
) : AccumulatorV2<Tuple2<Position<Double>?, Double?>, Tuple2<Position<Double>?, Double?>>() {
    override fun isZero(): Boolean {
        return !resettable || (position.position() == null && position.error() == null)
    }

    override fun copy(): AccumulatorV2<Tuple2<Position<Double>?, Double?>, Tuple2<Position<Double>?, Double?>> {
        return PositionAccumulator(
            MutablePosition.BestPosition(position.position(), position.error())
        )
    }

    override fun reset() {
        if (resettable)
            position.reset()
    }

    override fun add(value: Tuple2<Position<Double>?, Double?>) {
        if (value._1 != null && value._2 != null)
            position.mutate(value._1!!, value._2!!)
    }

    override fun merge(other: AccumulatorV2<Tuple2<Position<Double>?, Double?>, Tuple2<Position<Double>?, Double?>>) {
        // When we merge two position accumulators we just perform an addition with the current accumulator.
        add(other.value())
    }

    override fun value(): Tuple2<Position<Double>?, Double?> {
        return Tuple2(position.position(), position.error())
    }
}

class FitnessEvaluation(
    private val fitnessFunction: FitnessFunction<Position<Double>, Double>,
    private val bestGlobalPositionAccumulator: PositionAccumulator
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The fitness function is evaluated with the current particle's position.
        particle.error = fitnessFunction.evaluate(particle.position)

        // The personal best position is saved.
        if (particle.bestPersonalError == null
            || (particle.bestPersonalError != null && particle.bestPersonalError!! > particle.error!!)) {
            particle.bestPersonalPosition = particle.position.toMutableList()
            particle.bestPersonalError = particle.error
        }

        // The shared accumulator is updated by the executor.
        bestGlobalPositionAccumulator.add(Tuple2(particle.position, particle.error))

        return particle
    }
}

class AsyncFitnessEvaluation(
    private val fitnessFunction: FitnessFunction<Position<Double>, Double>,
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The fitness function is evaluated with the current particle's position.
        particle.error = fitnessFunction.evaluate(particle.position)

        // The personal best position is saved.
        if (particle.bestPersonalError == null
            || (particle.bestPersonalError != null && particle.bestPersonalError!! > particle.error!!)) {
            particle.bestPersonalPosition = particle.position.toMutableList()
            particle.bestPersonalError = particle.error
        }

        return particle
    }
}

class PositionEvaluation(
    private val bestGlobalPositionBroadcast: Broadcast<Tuple2<Position<Double>?, Double?>>
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

class AsyncPositionEvaluation(
    private val bestGlobalPositionAccumulator: PositionAccumulator
) : Function<Particle, Particle> {
    override fun call(particle: Particle): Particle {
        // The best global position is read from the broadcast variable copy on the executor.
        val bestGlobalTuple = bestGlobalPositionAccumulator.value()
        val bestGlobalPosition = bestGlobalTuple._1

        // The particle's velocity is updated.
        particle.updateVelocity(bestGlobalPosition)

        // The particle's position is updated.
        particle.updatePosition()

        return particle
    }
}