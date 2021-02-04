package pw.binom.repo

fun Array<String>.getParam(name: String) = find { it.startsWith("$name=") }?.removePrefix("$name=")
fun Array<String>.getFlag(name: String) = any { it == "-$name" }
fun Array<String>.getParams(name: String) = filter { it.startsWith("$name=") }.map { it.removePrefix("$name=") }
fun Array<String>.validValues(vararg args: String): Boolean = !args.any { p ->
    any { !it.startsWith("$p") }
}