package pw.binom.repo

class Version(
    val versions: List<Int>,
    val snapshot: Boolean,
) : Comparable<Version> {
    companion object {
        private const val SNAPSHOT = "-SNAPSHOT"
        fun parse(text: String): Version {
            var text = text
            val snapshot = text.endsWith(SNAPSHOT)
            if (snapshot) {
                text = text.substring(0, text.length - SNAPSHOT.length)
            }
            return Version(
                versions = text.split('.').map { it.toInt() },
                snapshot = snapshot,
            )
        }
    }

    override fun compareTo(other: Version): Int {
        for (i in maxOf(other.versions.size, versions.size) - 1 downTo 0) {
            val r = versions.getOrElse(i) { 0 }.compareTo(other.versions.getOrElse(i) { 0 })
            if (r != 0) {
                return r
            }
        }
        return 0
    }

    val asString: String
        get() {
            val sb = StringBuilder()
            versions.forEachIndexed { index, i ->
                if (index > 0) {
                    sb.append(".")
                }
                sb.append(i)
            }
            if (snapshot) {
                sb.append(SNAPSHOT)
            }
            return sb.toString()
        }

    override fun toString(): String = "Version($asString)"
}