package pw.styles

fun CSS(function: CSSBlock.() -> Unit): CSSBlock {
    val block = CSSBlock()
    block.function()
    return block
}

class CSSBlock {
    private val csses = ArrayList<CSSDef>()
    operator fun String.invoke(function: CSSDef.() -> Unit) {
        csses += style(this, false, function)
    }

    fun buildRecursive(output:Appendable) {
        csses.forEach { it.buildRecursive(output) }
    }

}

