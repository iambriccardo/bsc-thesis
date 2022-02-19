package util

import PosPlacementMatrix
import beautify
import org.apache.spark.api.java.JavaSparkContext
import scala.Tuple2
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
fun ExecutionResult<Tuple2<PosPlacementMatrix?, Double?>>.toFile(
    sparkContext: JavaSparkContext,
    configuration: Configuration
) {
    val fileRepresentation =
        "Execution results for [$executionName] with ${sparkContext.defaultParallelism()} partitions" +
                "\n" + "----" +
                "\n" + "Configuration:" +
                "\n" + "- ${configuration.iterations} iterations" +
                "\n" + "- ${configuration.particles} particles" +
                "\n" + "- ${configuration.fogNodes} fog nodes" +
                "\n" + "- ${configuration.modules} modules" +
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
                "\n" + output._1?.beautify() +
                "\n" + output._2

    writeFile(configuration, fileRepresentation)
}

@OptIn(ExperimentalTime::class)
fun <O> newTimedExecution(sparkContext: JavaSparkContext, block: Execution<O>.() -> Unit) {
    val execution = object : Execution<O> {
        override fun execute(executionName: String, block: () -> O): ExecutionResult<O> {
            println("Starting execution [$executionName]...")

            val timedValue = measureTimedValue {
                block()
            }

            println("Execution [$executionName] with ${sparkContext.defaultParallelism()} partitions finished in: ")
            println("- ${timedValue.duration.toDouble(DurationUnit.HOURS)} hours")
            println("- ${timedValue.duration.toDouble(DurationUnit.MINUTES)} minutes")
            println("- ${timedValue.duration.toDouble(DurationUnit.SECONDS)} seconds")
            println("- ${timedValue.duration.toDouble(DurationUnit.MILLISECONDS)} milliseconds")

            return ExecutionResult(executionName, timedValue.duration, timedValue.value)
        }
    }

    execution.block()
}

