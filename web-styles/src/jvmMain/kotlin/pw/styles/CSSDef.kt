package pw.styles

import kotlin.reflect.KProperty

open class CSSDef(val name: String, val parent: CSSDef?, val then: Boolean) {
    val fields = HashMap<String, String>()
    val childs = ArrayList<CSSDef>()

    var position by CssProperty()
    var left by CssProperty()
    var right by CssProperty()
    var top by CssProperty()
    var display by CssProperty()
    var backgroundColor by CssProperty()
    var width by CssProperty()
    var height by CssProperty()
    var cursor by CssProperty()
    var transitionDuration by CssProperty()
    var transitionProperty by CssProperty()
    var transform by CssProperty()
    var filter by CssProperty()
    var userSelect by CssProperty("-moz-user-select", "-ms-user-select", "-webkit-user-select")
    var fontFamily by CssProperty()
    var color by CssProperty()
    var fontSize by CssProperty()
    var marginTop by CssProperty()
    var fontWeight by CssProperty()
    var verticalAlign by CssProperty()
    var textAlign by CssProperty()
    var backgroundRepeat by CssProperty()
    var backgroundPosition by CssProperty()
    var border by CssProperty()
    var outline by CssProperty()
    var padding by CssProperty()
    var transitionTimingFunction by CssProperty()
    var overflow by CssProperty()

    operator fun String.compareTo(f: CSSDef.() -> Unit): Int {
        style(this, true, f)
        return 0
    }

    operator fun String.invoke(f: CSSDef.() -> Unit): Int {
        style(this, false, f)
        return 0
    }

    fun buildSelfPath(): String {
        if (parent == null)
            return name

        val sb = StringBuilder(parent.buildSelfPath())
        if (!then)
            sb.append(" ")
        sb.append(name)
        return sb.toString()
    }

    fun buildSelf(sb: Appendable) {
        sb.append(buildSelfPath()).append(" {\n")
        fields.forEach {
            sb.append("\t${convertKey(it.key)}: ${it.value};\n")
        }
        sb.append("}\n")
    }

    fun convertKey(key: String): String {
        val out = StringBuilder()
        for (i in 0..key.length - 1) {
            val c = key[i]
            val l = c.toLowerCase()
            if (c == l)
                out.append(c)
            else {
                out.append("-").append(l)
            }
        }
        return out.toString()
    }

    fun buildRecursive(sb: Appendable) {
        buildSelf(sb)

        childs.forEach {
            it.buildSelf(sb)
        }
    }
}

private val current = ThreadLocal<CSSDef>()

inline fun <T, R> ThreadLocal<T>.swap(value: T, f: () -> R): R {
    val oldValud = get()
    set(value)
    try {
        return f()
    } finally {
        set(oldValud)
    }
}

fun style(name: String, then: Boolean = false, f: CSSDef.() -> Unit): CSSDef {
    val def = CSSDef(name, current.get(), then)
    current.get()?.childs?.let { it.add(def) }
    current.swap(def) {
        def.f()
    }

    return def
}

class CssProperty(vararg val also: String) {
    operator fun getValue(thisRef: CSSDef, property: KProperty<*>): String? = thisRef.fields[property.name]
    operator fun setValue(thisRef: CSSDef, property: KProperty<*>, value: String?) {
        if (value == null) {
            thisRef.fields.remove(property.name)
            also.forEach {
                thisRef.fields.remove(it)
            }
        } else {
            thisRef.fields[property.name] = value
            also.forEach {
                thisRef.fields[it] = value
            }
        }
    }
}