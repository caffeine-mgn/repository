package pw.binom.repo.repositories.maven

import pw.binom.date.DateTime
import pw.binom.repo.maven.MavenVersion

data class MetaData(
    val groupId: MavenGroup,
    val artifactId: String,
    val version: MavenVersion?,
    val latest: MavenVersion?,
    val release: MavenVersion?,
    val versions: List<MavenVersion>,
    val lastUpdate: DateTime,
) {
    companion object {

        private fun versionMaxOf(a: MavenVersion?, b: MavenVersion?): MavenVersion? =
            when {
                a == null && b == null -> null
                a != null && b == null -> a
                a == null && b != null -> b
                a != null && b != null -> if (a > b) a else b
                else -> b
            }

        fun combine(group: MavenGroup, artifact: String, collection: Collection<MetaData>): MetaData {
            require(collection.isNotEmpty()) { "Collection cannot be empty" }
            if (collection.size == 1) {
                return collection.first()
            }
            return collection.reduce { acc, metaData ->
                val latest = versionMaxOf(acc.latest, metaData.latest)
                val release = versionMaxOf(acc.release, metaData.release)
                val version = versionMaxOf(acc.version, metaData.version)
                val lastUpdate = pw.binom.date.maxOf(acc.lastUpdate, metaData.lastUpdate)
                val versions = (acc.versions + metaData.versions).sorted()
                MetaData(
                    latest = latest,
                    release = release,
                    version = version,
                    lastUpdate = lastUpdate,
                    versions = versions,
                    groupId = group,
                    artifactId = artifact,
                )
            }
        }
    }
}