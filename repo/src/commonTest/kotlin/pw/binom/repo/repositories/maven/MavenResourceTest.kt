package pw.binom.repo.repositories.maven

import kotlin.test.Test

class MavenResourceTest {
    @Test
    fun test(){
        println(MavenResource.parseURI("pw/binom/anton/metadata.xml"))
        println(MavenResource.parseURI("pw/binom/anton/1.0.0/metadata.xml"))
        println(MavenResource.parseURI("anton/metadata.xml"))
        println(MavenResource.parseURI("anton/1.0.0/metadata.xml"))
    }
}