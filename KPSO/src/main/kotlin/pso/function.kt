package pso

class SampleFunction: Function<Double, Double> {
    override fun eval(inputs: List<Double>): Double {
        var result = 0.0

        inputs.forEach {
            result += it * it
        }

        Thread.sleep(1000)

        return result
    }
}

interface Function<in T, out E> {

    fun eval(inputs: List<T>): E
}