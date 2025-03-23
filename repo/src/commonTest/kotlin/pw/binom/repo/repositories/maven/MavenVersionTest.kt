package pw.binom.repo.repositories.maven

import pw.binom.repo.maven.MavenVersion
import pw.binom.testing.shouldBeFalse
import pw.binom.testing.shouldBeTrue
import kotlin.test.Test

class MavenVersionTest {
    @Test
    fun testIsVersion() {
        MavenVersion.isVersion("1.0").shouldBeTrue()
        MavenVersion.isVersion("1.0.1").shouldBeTrue()
        MavenVersion.isVersion("1").shouldBeTrue()
        MavenVersion.isVersion("0.9.0.M2").shouldBeTrue()
        MavenVersion.isVersion("1-SNAPSHOT").shouldBeTrue()
        MavenVersion.isVersion("1.0-SNAPSHOT").shouldBeTrue()
        MavenVersion.isVersion("1.0.0-SNAPSHOT").shouldBeTrue()
        MavenVersion.isVersion("anton").shouldBeFalse()
    }
}