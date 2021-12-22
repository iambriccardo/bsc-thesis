data class Configuration(
    val isSynchronous: Boolean = false,
    val iterations: Int = 0,
    val particles: Int = 0,
    val dimensionality: Int = 0,
    val outputPath: String,
    val keepAlive: Boolean = false,
)

fun Array<String>.asConfiguration(): Configuration {
    if (this.size < 6) throw RuntimeException("Not all the arguments have been provided.")

    return Configuration(
        this[0] == "sync",
        this[1].toInt(),
        this[2].toInt(),
        this[3].toInt(),
        this[4],
        this[5] == "true"
    )
}