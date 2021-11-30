package pso

typealias Swarm = MutableList<Particle>

class PSO(
    private val costFunction: Function<Double, Double>,
    private val x0: Position,
    private val bounds: Bounds,
    private val numberOfParticles: Int,
    private val maximumIterations: Int
) {

    private var globalBestPosition: Position = mutableListOf()
    private var globalBestError: Double? = null

    init {
        run()
    }

    private fun run() {
        optimize(spawnSwarm())
    }

    private fun spawnSwarm(): Swarm {
        return mutableListOf<Particle>().also { swarm ->
            repeat(numberOfParticles) {
                swarm.add(Particle(x0))
            }
        }
    }

    private fun optimize(swarm: Swarm) {
        repeat(maximumIterations) {
            println("Iteration #${it}")
            (0 until numberOfParticles).forEach { particleId ->
                swarm[particleId].eval(costFunction)

                if (globalBestError == null || swarm[particleId].error!! < globalBestError!!) {
                    println("Global before $globalBestPosition $globalBestError")
                    globalBestPosition = swarm[particleId].position.toMutableList()
                    globalBestError = swarm[particleId].error
                    println("Global after $globalBestPosition $globalBestError")
                }
            }

            (0 until numberOfParticles).forEach { particleId ->
                swarm[particleId].updateVelocity(globalBestPosition)
                swarm[particleId].updatePosition(bounds)
            }
        }

        println("Final Result:")
        println(globalBestPosition)
        println(globalBestError)
    }
}