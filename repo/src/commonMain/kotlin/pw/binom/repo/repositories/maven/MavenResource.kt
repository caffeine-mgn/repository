package pw.binom.repo.repositories.maven

import pw.binom.repo.maven.MavenVersion

sealed interface MavenResource {
    val group: MavenGroup
    val artifact: String
    val file: String

    companion object {
        fun parseURI(uri: String):MavenResource {
            val items = uri.split('/')
            val fileName = items.last()
            val b = items[items.size - 2]
            return if (MavenVersion.isVersion(b)) {
                val group = MavenGroup(items.subList(0, items.size - 3).joinToString("."))
                val artifact = items[items.size - 3]
                InVersion(
                    group = group,
                    artifact = artifact,
                    version = MavenVersion(b),
                    file = fileName,
                )
            } else {
                val group = MavenGroup(items.subList(0, items.size - 2).joinToString("."))
                val artifact = b
                InArtifact(
                    group = group,
                    artifact = artifact,
                    file = fileName,
                )
            }
        }
    }

    data class InArtifact(
        override val group: MavenGroup,
        override val artifact: String,
        override val file: String,
    ) : MavenResource

    data class InVersion(
        override val group: MavenGroup,
        override val artifact: String,
        val version: MavenVersion,
        override val file: String,
    ) : MavenResource
}