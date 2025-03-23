package pw.binom.repo

import pw.binom.date.DateTime
import pw.binom.repo.repositories.maven.MavenMetadata
import pw.binom.repo.maven.MavenVersion
import pw.binom.repo.repositories.maven.MavenGroup
import pw.binom.xml.serialization.Xml
import kotlin.test.Test

class MavenMedataTest {
    @Test
    fun aa() {
        val data = MavenMetadata(
            groupId = MavenGroup("binom-init"),
            artifactId = "binom-init.gradle.plugin",
            version = MavenVersion("1.0.0"),
            versioning = MavenMetadata.Versioning(
                snapshot = MavenMetadata.Snapshot(timestamp = DateTime.now, buildNumber = 8),
            )
        )
        val e = Xml().encodeToString(MavenMetadata.serializer(), data)
        val o = Xml().decodeFromString(MavenMetadata.serializer(), e)
        println(e)
    }
}