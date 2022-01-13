import kotlin.random.Random

fun randomPlacement(nParticles: Int, nFogNodes: Int, nModules: Int): List<Particle> {
    val particles = mutableListOf<Particle>()

    repeat(nParticles) {
        val particle = Particle(
            randomPosPlacementMatrix(nFogNodes, nModules),
            null,
            null,
            null,
            randomVelPlacementMatrix(nFogNodes, nModules)
        )

        particles.add(particle)
    }

    return particles
}

fun randomPosPlacementMatrix(nFogNodes: Int, nModules: Int): PosPlacementMatrix {
    val placementMatrix = mutableListOf<IntArray>()
    val assignedModules = IntArray(nModules)

    for (i in 0 until nFogNodes) {
        val moduleAllocations = IntArray(nModules)

        for (j in 0 until nModules) {
            if (assignedModules[j] == 0) {
                moduleAllocations[j] = Random.nextInt(2)

                if (moduleAllocations[j] == 1) {
                    assignedModules[j] = 1
                }
            } else {
                moduleAllocations[j] = 0
            }
        }

        placementMatrix.add(moduleAllocations)
    }

    return placementMatrix.assignDanglingModules(assignedModules)
}

fun randomVelPlacementMatrix(nFogNodes: Int, nModules: Int): VelPlacementMatrix {
    val placementMatrix = mutableListOf<DoubleArray>()
    val assignedModules = IntArray(nModules)

    for (i in 0 until nFogNodes) {
        val moduleAllocations = DoubleArray(nModules)

        for (j in 0 until nModules) {
            if (assignedModules[j] == 0) {
                moduleAllocations[j] = Random.nextDouble(2.0)

                if (moduleAllocations[j] == 1.0) {
                    assignedModules[j] = 1
                }
            } else {
                moduleAllocations[j] = 0.0
            }
        }

        placementMatrix.add(moduleAllocations)
    }

    return placementMatrix
}

//fun randomParticlesOfDouble(numberOfParticles: Int, dimensionality: Int): List<Particle> {
//    val particles = mutableListOf<Particle>()
//
//    repeat(numberOfParticles) {
//        val particle = Particle(
//            randomPositionOfDouble(dimensionality),
//            null,
//            null,
//            null,
//            randomVelocityOfDouble(dimensionality)
//        )
//
//        particles.add(particle)
//    }
//
//    return particles
//}
//
//fun randomPositionOfDouble(dimensionality: Int): Position<Double> {
//    val position = mutableListOf<Double>()
//
//    repeat(dimensionality) {
//        position.add(5.0)
//    }
//
//    return position
//}
//
//fun randomVelocityOfDouble(dimensionality: Int): Velocity<Double> {
//    val velocity = mutableListOf<Double>()
//
//    repeat(dimensionality) {
//        velocity.add(1.0)
//    }
//
//    return velocity
//}