fun randomParticlesOfDouble(numberOfParticles: Int, dimensionality: Int): List<Particle> {
    val particles = mutableListOf<Particle>()

    repeat((0 until numberOfParticles).count()) {
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
    val position = emptyPosition<Double>()

    repeat((0 until dimensionality).count()) {
        position.add(5.0)
    }

    return position
}

fun randomVelocityOfDouble(dimensionality: Int): Velocity<Double> {
    val velocity = emptyPosition<Double>()

    repeat((0 until dimensionality).count()) {
        velocity.add(1.0)
    }

    return velocity
}