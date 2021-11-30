import java.io.Serializable
import kotlin.random.Random

typealias Position<T> = MutableList<T>

fun <T> emptyPosition(): Position<T> {
    return mutableListOf()
}

interface MutablePosition<T> : Serializable {
    fun mutate(position: Position<T>, error: T)
    fun reset()

    fun position(): Position<T>?
    fun error(): T?

    class BestPosition(
        private var bestPosition: Position<Double>? = null,
        private var bestError: Double? = null
    ) : MutablePosition<Double> {

        override fun mutate(position: Position<Double>, error: Double) {
            if (bestError == null || (bestError != null && bestError!! > error)) {
                bestPosition = position.toMutableList()
                bestError = error
            }
        }

        override fun reset() {
            bestPosition = null
            bestError = null
        }

        override fun position() = bestPosition

        override fun error() = bestError
    }
}

typealias Velocity<T> = MutableList<T>

fun <T> emptyVelocity(): Velocity<T> {
    return mutableListOf()
}

fun Particle.updateVelocity(bestGlobalPosition: Position<Double>?) {
    val numberOfDimensions = position.size
    val w = 0.729
    val c1 = 1.49445
    val c2 = 1.49445

    (0 until numberOfDimensions).forEach {
        val r1 = Random.nextDouble()
        val r2 = Random.nextDouble()

        val cognitiveVelocity = c1 * r1 * (bestPersonalPosition!![it] - position[it])
        val socialVelocity = c2 * r2 * (bestGlobalPosition!![it] - position[it])

        velocity[it] = w * velocity[it] + cognitiveVelocity + socialVelocity
    }
}

fun Particle.updatePosition() {
    val numberOfDimensions = position.size

    (0 until numberOfDimensions).forEach {
        position[it] = position[it] + velocity[it]
        // TODO: implement bounds.
    }
}

data class Particle(
    var position: Position<Double>,
    var error: Double?,
    var bestPersonalPosition: Position<Double>?,
    var bestPersonalError: Double?,
    var velocity: Velocity<Double>
) : Serializable

interface FitnessFunction<I, O> : Serializable {
    fun evaluate(input: I): O

    class Sphere : FitnessFunction<Position<Double>, Double> {
        override fun evaluate(input: Position<Double>): Double {
            var result = 0.0

            input.forEach {
                result += it * it
            }

            Thread.sleep(1000)

            return result
        }
    }
}

