package util

import java.io.File

fun writeFile(config: Configuration, content: String) {
    println("Writing file to ${config.resultPath}...")

    val output = File(config.resultPath)
    output.parentFile.mkdirs()
    output.createNewFile()
    output.writeText(content)

    println("File written to ${config.resultPath}")
}

fun keepJVMAlive() {
    while (true) {

    }
}