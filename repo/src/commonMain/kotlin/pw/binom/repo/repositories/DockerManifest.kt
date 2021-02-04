package pw.binom.repo.repositories

import kotlinx.serialization.Serializable

@Serializable
data class DockerManifest(
    val schemaVersion: Int,
    val mediaType: String,
    val config: Blob,
    val layers: List<Blob>,
) {
    @Serializable
    data class Blob(val mediaType: String, val size: Long, val digest: String)
}