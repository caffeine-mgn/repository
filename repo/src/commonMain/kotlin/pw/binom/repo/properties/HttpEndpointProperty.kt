package pw.binom.repo.properties

import kotlinx.serialization.Serializable
import pw.binom.validate.annotations.OneOf

@Serializable
class HttpEndpointProperty(
    val port: Int,
    val middlewares: List<Middleware> = emptyList(),
    val repository: Repository,
) {

    @Serializable
    @OneOf("maven", "docker")
    data class Repository(
        val maven: String? = null,
        val docker: String? = null,
    )

    @Serializable
    @OneOf("urlPrefix", "host", "method")
    data class Middleware(
        val urlPrefix: String? = null,
        val host: String? = null,
        val method: String? = null,
    )
}