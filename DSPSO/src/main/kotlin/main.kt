import com.xenomachina.argparser.ArgParser
import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaSparkContext
import pso.PSO
import scala.Tuple2
import util.*

fun main(args: Array<String>) {
    ArgParser(args).parseInto(::Configuration).run {
        val spark = SparkConf()
            .setAppName("DSPSO")
            .set("spark.scheduler.mode", "FAIR") // We allow multiple jobs to be executed in a round robin fashion.
            .set("spark.kubernetes.driver.annotation.sidecar.istio.io/inject", "false")
            .set("spark.kubernetes.executor.annotation.sidecar.istio.io/inject", "false")

        // We can run the algorithm with a local simulated cluster, where the number of executors
        // is equal to the number of core of the running host.
        if (localMaster) spark.setMaster("local[*]")

        val sc = JavaSparkContext(spark)

        newTimedExecution<Tuple2<Position<Double>?, Double?>> {
            when (algorithmType) {
                AlgorithmType.NORMAL -> execute("NORMAL PSO") { PSO.normal(this@run) }
                AlgorithmType.SYNC -> execute("SYNCHRONOUS PSO") { PSO.sync(this@run, sc) }
                AlgorithmType.ASYNC -> execute("ASYNCHRONOUS PSO") { PSO.async(this@run, sc) }
            }.toFile(this@run)
        }

        if (keepAlive) {
            println("Keeping the JVM alive...")
            keepJVMAlive()
        }
    }
}