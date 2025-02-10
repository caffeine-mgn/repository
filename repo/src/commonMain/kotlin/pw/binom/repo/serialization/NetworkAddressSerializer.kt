package pw.binom.repo.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.io.socket.DomainSocketAddress

object NetworkAddressSerializer : KSerializer<DomainSocketAddress> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("NetworkAddressSerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DomainSocketAddress {
        val str = decoder.decodeString()
        val items = str.split(':', limit = 2)
        return DomainSocketAddress(
            host = items[0],
            port = items.getOrNull(1)?.toIntOrNull()
                ?: throw SerializationException("Invalid NetworkAddress \"$str\"")
        )
    }

    override fun serialize(encoder: Encoder, value: DomainSocketAddress) {
        encoder.encodeString("${value.host}:${value.port}")
    }
}