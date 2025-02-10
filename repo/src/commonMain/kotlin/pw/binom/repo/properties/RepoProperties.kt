package pw.binom.repo.properties

import kotlinx.serialization.Serializable
import pw.binom.properties.serialization.annotations.PropertiesPrefix

@Serializable
@PropertiesPrefix("repo")
data class RepoProperties(
    val endpoints: Map<String, HttpEndpointProperty>,
    val repositories: Repositories,
) {
    @Serializable
    class Repositories(
        val maven: Map<String, MavenRepositoryProperty>,
    )
}