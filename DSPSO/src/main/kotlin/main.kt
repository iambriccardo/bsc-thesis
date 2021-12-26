import org.apache.spark.SparkConf
import org.apache.spark.api.java.JavaSparkContext
import pso.PSO
import scala.Tuple2
import util.asConfiguration
import util.keepJVMAlive
import util.newTimedExecution
import util.toFile

fun main(args: Array<String>) {
    val config = args.asConfiguration()

    val spark = SparkConf()
        .setMaster("local[*]")
        .setAppName("DSPSO")
        .set("spark.scheduler.mode", "FAIR") // We allow multiple jobs to be executed in a round robin fashion.
        .set("spark.kubernetes.driver.annotation.sidecar.istio.io/inject", "false")
        .set("spark.kubernetes.executor.annotation.sidecar.istio.io/inject", "false")

    val sc = JavaSparkContext(spark)

    newTimedExecution<Tuple2<Position<Double>?, Double?>> {
        when (config.isSynchronous) {
            true -> execute("SYNCHRONOUS PSO") { PSO.sync(config, sc) }
            false -> execute("ASYNCHRONOUS PSO") { PSO.async(config, sc) }
        }.toFile(config)
    }

    if (config.keepAlive) {
        println("Keeping the JVM alive...")
        keepJVMAlive()
    }
}