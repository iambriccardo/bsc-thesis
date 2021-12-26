fun randomParticlesOfDouble(numberOfParticles: Int, dimensionality: Int): List<Particle> {
    val particles = mutableListOf<Particle>()

    repeat(numberOfParticles) {
        val particle = Particle(
            randomPositionOfDouble(dimensionality),
            null,
            null,
            null,
            randomVelocityOfDouble(dimensionality)
        )

        particles.add(particle)
    }

    return particles
}

fun randomPositionOfDouble(dimensionality: Int): Position<Double> {
    val position = mutableListOf<Double>()

    repeat(dimensionality) {
        position.add(5.0)
    }

    return position
}

fun randomVelocityOfDouble(dimensionality: Int): Velocity<Double> {
    val velocity = mutableListOf<Double>()

    repeat(dimensionality) {
        velocity.add(1.0)
    }

    return velocity
}