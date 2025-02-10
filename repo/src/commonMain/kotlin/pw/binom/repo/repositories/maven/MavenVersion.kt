package pw.binom.repo.repositories.maven

import kotlin.jvm.JvmInline

@JvmInline
value class MavenVersion(val raw: String) : Comparable<MavenVersion> {
    companion object {
        private const val SNAPSHOT = "-SNAPSHOT"
    }

    val isSnapshot
        get() = raw.endsWith(SNAPSHOT)

    val elements
        get() = (if (isSnapshot) raw.substring(SNAPSHOT.length) else raw).splitToSequence('.')

    val asString
        get() = raw

    override fun compareTo(other: MavenVersion): Int {
        val self = elements.map { it.toIntOrNull()?:0 }.toList()
        val other = other.elements.map { it.toIntOrNull()?:0 }.toList()
        for (i in maxOf(other.size, self.size) - 1 downTo 0) {
            val r = self.getOrElse(i) { 0 }.compareTo(other.getOrElse(i) { 0 })
            if (r != 0) {
                return r
            }
        }
        return 0
    }
}