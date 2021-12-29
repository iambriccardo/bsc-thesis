package util

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

interface Execution<O> {
    fun execute(executionName: String, block: () -> O): ExecutionResult<O>
}

data class ExecutionResult<O> @OptIn(ExperimentalTime::class) constructor(
    val executionName: String,
    val duration: Duration,
    val output: O
)

@OptIn(ExperimentalTime::class)
fun <O> ExecutionResult<O>.toFile(configuration: Configuration) {
    val fileRepresentation = "Execution result for [$executionName]" +
            "\n" + "----" +
            "\n" + "Configuration:" +
            "\n" + "- ${configuration.iterations} iterations" +
            "\n" + "- ${configuration.particles} particles" +
            "\n" + "- ${configuration.dimensionality} dimensions" +
            "\n" + "- ${configuration.superRDDSize} particles per super rdd" +
            "\n" + "- ${configuration.fitnessEvalDelay} fitness evaluation delay in ms" +
            "\n" + "----" +
            "\n" + "Duration:" +
            "\n" + "- ${duration.toDouble(DurationUnit.HOURS)} hours" +
            "\n" + "- ${duration.toDouble(DurationUnit.MINUTES)} minutes" +
            "\n" + "- ${duration.toDouble(DurationUnit.SECONDS)} seconds" +
            "\n" + "- ${duration.toDouble(DurationUnit.MILLISECONDS)} milliseconds" +
            "\n" + "----" +
            "\n" + "Output:" +
            "\n" + output.toString()

    writeFile(configuration, fileRepresentation)
}

@OptIn(ExperimentalTime::class)
fun <O> newTimedExecution(block: Execution<O>.() -> Unit) {
    val execution = object : Execution<O> {
        override fun execute(executionName: String, block: () -> O): ExecutionResult<O> {
            println("Starting execution [$executionName]...")

            val timedValue = measureTimedValue {
                block()
            }

            println("Execution [$executionName] finished in: ")
            println("- ${timedValue.duration.toDouble(DurationUnit.HOURS)} hours")
            println("- ${timedValue.duration.toDouble(DurationUnit.MINUTES)} minutes")
            println("- ${timedValue.duration.toDouble(DurationUnit.SECONDS)} seconds")
            println("- ${timedValue.duration.toDouble(DurationUnit.MILLISECONDS)} milliseconds")

            return ExecutionResult(executionName, timedValue.duration, timedValue.value)
        }
    }

    execution.block()
}

