package pw.styles

import java.io.File

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        File(args[0]).outputStream().bufferedWriter().use {
            style.buildRecursive(it)
        }
    }
}