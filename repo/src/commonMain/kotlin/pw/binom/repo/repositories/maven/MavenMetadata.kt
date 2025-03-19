package pw.binom.repo.repositories.maven

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pw.binom.date.DateTime
import pw.binom.repo.maven.MavenVersion
import pw.binom.repo.repositories.maven.serialization.DateTimeSerializer
import pw.binom.xml.serialization.annotations.XmlNode
import pw.binom.xml.serialization.annotations.XmlWrapper

@SerialName("metadata")
@Serializable
data class MavenMetadata(
    @XmlNode
    val groupId: MavenGroup,
    @XmlNode
    val artifactId: String,
    @XmlNode
    val version: MavenVersion?,
    val modelVersion: String = "1.1.0",
    val versioning: Versioning?,
) {
    @Serializable
    data class Versioning(
        val latest: MavenVersion? = null,
        val release: MavenVersion? = null,
        val snapshot: Snapshot? = null,
        @Serializable(DateTimeSerializer::class)
        val lastUpdated: DateTime? = null,
        @XmlWrapper("versions")
        val versions: List<MavenVersion> = emptyList(),
    ) {
        companion object {
            fun combine(a: Versioning?, b: Versioning?): Versioning? {
                val latest = versionMaxOf(a?.latest, b?.latest)
                val release = versionMaxOf(a?.release, b?.release)
                val lastUpdate = versionMaxOf(a?.lastUpdated, b?.lastUpdated)
                val versions = ((a?.versions ?: emptyList()) + (b?.versions
                    ?: emptyList())).sorted()
                return if (latest == null && release == null && lastUpdate == null && versions.isNotEmpty()) {
                    null
                } else {
                    Versioning(
                        latest = latest,
                        release = release,
                        snapshot = Snapshot.combine(
                            a = a?.snapshot,
                            b = b?.snapshot,
                        ),
                        lastUpdated = lastUpdate,
                        versions = versions,
                    )
                }
            }
        }
    }

    @Serializable
    data class Snapshot(
        @Serializable(DateTimeSerializer::class)
        @XmlNode
        val timestamp: DateTime,
        @XmlNode
        val buildNumber: Int,
    ) {
        companion object {
            fun combine(a: Snapshot?, b: Snapshot?): Snapshot? {
                val snapshotTimestamp = versionMaxOf(
                    a?.timestamp,
                    b?.timestamp
                )
                val snapshotBuildNumber = versionMaxOf(
                    a?.buildNumber,
                    b?.buildNumber
                )
                return if (snapshotTimestamp != null && snapshotBuildNumber != null) {
                    Snapshot(
                        timestamp = snapshotTimestamp,
                        buildNumber = snapshotBuildNumber
                    )
                } else {
                    null
                }
            }
        }
    }

    companion object {

        private inline fun <T : Comparable<T>> versionMaxOf(a: T?, b: T?): T? =
            when {
                a == null && b == null -> null
                a != null && b == null -> a
                a == null && b != null -> b
                a != null && b != null -> if (a > b) a else b
                else -> b
            }

        fun combine(collection: Collection<MavenMetadata>): MavenMetadata {
            require(collection.isNotEmpty()) { "Collection cannot be empty" }
            if (collection.size == 1) {
                return collection.first()
            }
            val first = collection.first()
            val group = first.groupId
            val artifactId = first.artifactId
            collection.forEach {
                require(group == it.groupId)
                require(artifactId == it.artifactId)
            }
            return collection.reduce { acc, metaData ->
                val version = versionMaxOf(acc.version, metaData.version)
                MavenMetadata(
                    groupId = group,
                    artifactId = artifactId,
                    version = version,
                    versioning = Versioning.combine(a = acc.versioning, metaData.versioning),
                )
            }
        }
    }
}