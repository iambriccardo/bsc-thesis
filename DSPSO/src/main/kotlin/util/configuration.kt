package util

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default

enum class AlgorithmType {
    NORMAL,
    SYNC,
    ASYNC
}

class Configuration(parser: ArgParser) {
    val algorithmType by parser.mapping(
        "--normal" to AlgorithmType.NORMAL,
        "--sync" to AlgorithmType.SYNC,
        "--async" to AlgorithmType.ASYNC,
        help = "type of the algorithm"
    )

    val distributedPosEval by parser.flagging(
        "--distributed-pos-eval",
        help = "enable distributed position evaluation (only for sync)"
    )

    val iterations by parser.storing(
        "--iterations",
        help = "number of iterations"
    )

    val particles by parser.storing(
        "--particles",
        help = "number of particles"
    )

    val dimensionality by parser.storing(
        "--dimensionality",
        help = "dimensions of each particle"
    )

    val superRDDSize by parser.storing(
        "--super-rdd-size",
        help = "size of the super rdd"
    ).default("N/A")

    val resultPath by parser.storing(
        "--result-path",
        help = "path + filename where the result will be written"
    )

    val keepAlive by parser.flagging(
        "--keep-alive",
        help = "keep alive the JVM after the algorithm is finished"
    )

    val localMaster by parser.flagging(
        "--local-master",
        help = "runs the program with the master local[*]"
    )
}