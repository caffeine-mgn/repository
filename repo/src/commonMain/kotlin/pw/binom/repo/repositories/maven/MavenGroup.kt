package pw.binom.repo.repositories.maven

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import pw.binom.io.file.File
import pw.binom.url.toPath
import kotlin.jvm.JvmInline

@JvmInline
@Serializable(MavenGroup.Serializer::class)
value class MavenGroup(val raw: String) {
    object Serializer : KSerializer<MavenGroup> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("MavenGroup", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): MavenGroup =
            MavenGroup(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: MavenGroup) {
            encoder.encodeString(value.raw)
        }

    }

    fun resolve(root: File) =
        root.relative(raw.replace('.', '/'))

    val asString
        get() = raw
    val asPath
        get() = raw.replace('.', '/').toPath
}