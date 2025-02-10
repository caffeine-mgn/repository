package pw.binom.repo.repositories.maven

import pw.binom.io.file.File
import pw.binom.url.toPath
import kotlin.jvm.JvmInline

@JvmInline
value class MavenGroup(val raw: String) {
    fun resolve(root: File) =
        root.relative(raw.replace('.', '/'))

    val asString
        get() = raw
    val asPath
        get() = raw.replace('.', '/').toPath
}