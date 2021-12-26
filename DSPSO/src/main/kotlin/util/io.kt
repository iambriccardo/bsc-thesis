package util

import java.io.File

fun writeFile(config: Configuration, content: String) {
    println("Writing file to ${config.outputPath}...")

    val output = File(config.outputPath)
    output.parentFile.mkdirs()
    output.createNewFile()
    output.writeText(content)

    println("File written to ${config.outputPath}")
}

fun keepJVMAlive() {
    while(true) {

    }
}