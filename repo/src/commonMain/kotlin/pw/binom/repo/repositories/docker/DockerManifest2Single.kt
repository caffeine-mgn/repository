package pw.binom.repo.repositories.docker

import kotlinx.serialization.Serializable

@Serializable
data class DockerManifest2Single(
    val schemaVersion: Int,
    val mediaType: String,
    val config: Config,
    val layers: List<Blob> = emptyList(),
) {
    @Serializable
    data class Blob(val mediaType: String, val size: Long, val digest: String, val urls: List<String> = emptyList())

    @Serializable
    data class Config(val mediaType: String, val size: Long, val digest: String)
}