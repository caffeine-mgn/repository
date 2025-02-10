package pw.binom.repo

import kotlinx.serialization.Serializable
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.repo.serialization.NetworkAddressSerializer
import pw.binom.validate.annotations.Greater
import pw.binom.validate.annotations.NotBlank
import pw.binom.validate.annotations.OneOf

@Serializable
data class Proxy(
    @Serializable(NetworkAddressSerializer::class)
    val address: DomainSocketAddress,
    val auth: Auth? = null,
    val onlyFor: List<String>? = null,
    val noProxy: List<String>? = null,
    @Greater("0")
    val bufferSize: Int = DEFAULT_BUFFER_SIZE
){
    @Serializable
    @OneOf(
        "basicAuth",
        "bearerAuth",
    )
    data class Auth(
        val basicAuth: BasicAuth? = null,
        val bearerAuth: BearerAuth? = null,
    )

    @Serializable
    data class BasicAuth(
        @NotBlank
        val user: String,
        @NotBlank
        val password: String,
    )

    @Serializable
    data class BearerAuth(
        @NotBlank
        val token: String,
    )
}