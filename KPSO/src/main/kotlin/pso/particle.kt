package pso

import kotlin.random.Random

typealias Position = MutableList<Double>
typealias Velocity = MutableList<Double>

data class Bound(val min: Double, val max: Double)
typealias Bounds = MutableList<Bound>

class Particle(private val x0: Position) {
    var position: Position = mutableListOf()
    var error: Double? = null

    var personalBestPosition: Position = mutableListOf()
    var personalBestError: Double? = null

    var velocity: Velocity = mutableListOf()

    private val numberOfDimensions: Int = x0.size

    init {
        x0.forEach {
            velocity.add(1.0)
            position.add(it)
        }
    }

    fun eval(costFunction: Function<Double, Double>) {
        error = costFunction.eval(position)

        // TODO: implement in more idiomatic fashion
        if (personalBestError == null || error!! < personalBestError!!) {
            personalBestPosition = position
            personalBestError = error
        }
    }

    fun updateVelocity(globalBestPosition: Position) {
        val w = 0.729
        val c1 = 1.49445
        val c2 = 1.49445

        (0 until numberOfDimensions).forEach {
            val r1 = Random.nextDouble()
            val r2 = Random.nextDouble()

            val cognitiveVelocity = c1 * r1 * (personalBestPosition[it] - position[it])
            val socialVelocity = c2 * r2 * (globalBestPosition[it] - position[it])

            velocity[it] = w * velocity[it] + cognitiveVelocity + socialVelocity
        }
    }

    fun updatePosition(bounds: Bounds) {
        (0 until numberOfDimensions).forEach {
            position[it] = position[it] + velocity[it]

//            if (position[it] > bounds[it].max) {
//                position[it] = bounds[it].max
//            }
//
//            if (position[it] < bounds[it].min) {
//                position[it] = bounds[it].min
//            }
        }
    }
}