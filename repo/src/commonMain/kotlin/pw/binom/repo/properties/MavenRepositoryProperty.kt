package pw.binom.repo.properties

import kotlinx.serialization.Serializable
import pw.binom.repo.serialization.URLSerializer
import pw.binom.url.URL
import pw.binom.validate.annotations.OneOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
@OneOf(
    "file",
    "http",
    "cache",
    "combine",
)
class MavenRepositoryProperty(
    val file: FileStorage? = null,
    val http: HttpStorage? = null,
    val cache: Cache? = null,
    val combine: Combine? = null,
) {
    @Serializable
    data class FileStorage(val root: String, val readOnly: Boolean = false)

    @Serializable
    data class HttpStorage(
        @Serializable(URLSerializer::class)
        val url: URL,
        val auth: HttpAuthProperties? = null,
        val getTimeout: Duration = 10.seconds,
        val readOnly: Boolean = false,
    )

    @Serializable
    data class Cache(
        val source: String,
        val storage: String,
        val readOnly: Boolean = true,
        val copingTimeout: Duration = 1.minutes,
    )

    @Serializable
    data class Combine(val readFrom: List<String> = emptyList())
}