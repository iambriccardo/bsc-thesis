import pso.Bound
import pso.PSO
import pso.SampleFunction

fun main(args: Array<String>) {
    executePSO()
}

private fun executePSO() {
    PSO(
        costFunction = SampleFunction(),
        x0 = mutableListOf(0.0,0.0),
        bounds = mutableListOf(Bound(-10.0,10.0), Bound(-10.0,10.0)),
        numberOfParticles = 50,
        maximumIterations = 100
    )
}