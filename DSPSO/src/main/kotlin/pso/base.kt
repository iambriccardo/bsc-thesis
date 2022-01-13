import java.io.Serializable
import kotlin.math.exp
import kotlin.random.Random

typealias PosPlacementMatrix = Position<IntArray>
typealias VelPlacementMatrix = Position<DoubleArray>
typealias Position<T> = MutableList<T>
typealias Velocity<T> = MutableList<T>

interface MutablePosition<P, E> : Serializable {
    fun mutate(position: P, error: E)
    fun reset()

    fun position(): P?
    fun error(): E?

    class BestPosition(
        private var bestPosition: PosPlacementMatrix? = null,
        private var bestError: Double? = null
    ) : MutablePosition<PosPlacementMatrix, Double> {

        override fun mutate(position: PosPlacementMatrix, error: Double) {
            if (bestError == null || bestError!! > error) {
                // TODO: check if a copy is performed for the best global.
                bestPosition = position.copy()
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

data class Particle(
    var position: PosPlacementMatrix,
    var error: Double?,
    var bestPersonalPosition: PosPlacementMatrix?,
    var bestPersonalError: Double?,
    var velocity: VelPlacementMatrix
) : Serializable

fun Particle.updateBestPersonalPosition() {
    if (bestPersonalError == null || (bestPersonalError!! > error!!)) {
        bestPersonalPosition = position.copy()
        bestPersonalError = error
    }
}

fun Particle.updateVelocity(bestGlobalPosition: PosPlacementMatrix?) {
    val nFogNodes = velocity.size
    val nModules = velocity[0].size
    val assignedModules = IntArray(nModules)

    val minVelocity = 0.0
    val maxVelocity = 1.0
    val w = 0.729
    val c1 = 1.49445
    val c2 = 1.49445

    // We iterate for each fog node.
    for (i in 0 until nFogNodes) {
        val placements = position[i]
        val velocities = velocity[i]
        val bestPersonalPlacements = bestPersonalPosition!![i]
        val bestGlobalPlacements = bestGlobalPosition!![i]

        // We iterate for each module in a specific fog node.
        for (j in 0 until nModules) {
            val r1 = Random.nextDouble()
            val r2 = Random.nextDouble()

            val cognitiveVelocity = c1 * r1 * (bestPersonalPlacements[j] - placements[j])
            val socialVelocity = c2 * r2 * (bestGlobalPlacements[j] - placements[j])

            if (assignedModules[j] == 0) {
                var newVelocity = (w * velocities[j] + cognitiveVelocity + socialVelocity)
                if (newVelocity < minVelocity) {
                    newVelocity = minVelocity
                } else if (newVelocity > maxVelocity) {
                    newVelocity = maxVelocity
                }

                if (newVelocity == 1.0) {
                    assignedModules[j] = 1
                }
            } else {
                velocities[j] = 0.0
            }
        }
    }
}

fun Particle.updatePosition() {
    val nFogNodes = position.size
    val nModules = position[0].size
    val assignedModules = IntArray(nModules)

    for (i in 0 until nFogNodes) {
        val placements = position[i]
        val velocities = velocity[i]

        for (j in 0 until nModules) {
            if (assignedModules[j] == 0) {
                val random = Random.nextInt(2)
                val sigmoid = 1 / (1 + exp(-velocities[j]))

                if (sigmoid > random) {
                    placements[j] = 1
                } else {
                    placements[j] = 0
                }

                if (placements[j] == 1) {
                    assignedModules[j] = 1
                }
            } else {
                placements[j] = 0
            }
        }
    }

    position.assignDanglingModules(assignedModules)
}

fun PosPlacementMatrix.copy(): PosPlacementMatrix {
    val nFogNodes = size
    val nModules = this[0].size

    val placementMatrix = mutableListOf<IntArray>()

    for (i in 0 until nFogNodes) {
        val moduleAllocations = IntArray(nModules)

        for (j in 0 until nModules) {
            moduleAllocations[j] = this[i][j]
        }

        placementMatrix.add(moduleAllocations)
    }

    return placementMatrix
}

fun PosPlacementMatrix.beautify(): String {
    val nFogNodes = size
    val nModules = this[0].size

    val builder = StringBuilder()

    for (i in 0 until nModules) {
        builder.append("-")
    }
    builder.append("\n")

    for (i in 0 until nFogNodes) {
        for (j in 0 until nModules) {
            builder.append("${this[i][j]} ")
        }

        builder.append("\n")
    }

    for (i in 0 until nModules) {
        builder.append("-")
    }

    return builder.toString()
}

fun PosPlacementMatrix.assignDanglingModules(assignedModules: IntArray): PosPlacementMatrix {
    // Loop over all the assigned modules array and randomly allocate
    // any dangling module which has not been previously assigned.
    for (i in assignedModules.indices) {
        if (assignedModules[i] == 0) {
            // We randomly select a fog node on which we are going to install the ith module.
            val n: Int = Random.nextInt(this.size)
            this[n][i] = 1
            assignedModules[i] = 1
        }
    }

    return this
}

interface FitnessFunction<I, O> : Serializable {
    fun evaluate(input: I): O

    fun delayedEvaluation(delay: Long, input: I): O {
        Thread.sleep(delay)
        return evaluate(input)
    }

    class Sphere : FitnessFunction<Position<Double>, Double> {
        override fun evaluate(input: Position<Double>): Double {
            var result = 0.0

            input.forEach {
                result += it * it
            }

            return result
        }
    }

    class PlacementCost : FitnessFunction<PlacementCost.PlacementInput, Double> {
        data class PlacementInput(
            val placementMatrix: PosPlacementMatrix,
            val runtimesMatrix: MutableList<DoubleArray>,
        )

        override fun evaluate(input: PlacementInput): Double {
            val placementMatrix = input.placementMatrix
            val runtimesMatrix = input.runtimesMatrix

            val nFogNodes = placementMatrix.size
            val nModules = placementMatrix[0].size

            val sum = DoubleArray(nModules)

            for (i in 0 until nFogNodes) {
                val placements = placementMatrix[i]
                val runtimes = runtimesMatrix[i]

                for (j in 0 until nModules) {
                    if (placements[j] == 1) {
                        sum[i] += runtimes[j]
                    }
                }
            }

            return sum.maxOrNull() ?: 0.0
        }
    }
}

