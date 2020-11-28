package pw.binom.repo

class RemoteAddr(val host: String, val port: Int) {
    companion object {
        fun parse(str: String): RemoteAddr? {
            val p = str.lastIndexOf(':')
            if (p == -1)
                return null
            return RemoteAddr(
                    host = str.substring(0, p),
                    port = str.substring(p + 1).toIntOrNull() ?: return null
            )
        }
    }

    override fun toString(): String = "$host:$port"
}