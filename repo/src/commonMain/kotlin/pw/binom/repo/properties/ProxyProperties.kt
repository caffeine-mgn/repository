package pw.binom.repo.properties

import kotlinx.serialization.Serializable
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.properties.serialization.annotations.PropertiesPrefix
import pw.binom.repo.serialization.NetworkAddressSerializer
import pw.binom.validate.annotations.Greater
import pw.binom.validate.annotations.NotBlank
import pw.binom.validate.annotations.OneOf

@Serializable
@PropertiesPrefix("repo")
class ProxyProperties(
    val proxy: Proxy? = null,
) {
    @Serializable
    data class Proxy(
        @Serializable(NetworkAddressSerializer::class)
        val address: DomainSocketAddress,
        val auth: HttpAuthProperties? = null,
        val onlyFor: List<String>? = null,
        val noProxy: List<String>? = null,
        @Greater("0")
        val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    )
}