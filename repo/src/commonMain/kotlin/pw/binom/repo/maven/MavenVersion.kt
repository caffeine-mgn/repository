package pw.binom.repo.maven

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.jvm.JvmInline

@JvmInline
@Serializable(MavenVersion.Serializer::class)
value class MavenVersion(val raw: String) : Comparable<MavenVersion> {
    object Serializer : KSerializer<MavenVersion> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("MavenVersion", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): MavenVersion =
            MavenVersion(decoder.decodeString())

        override fun serialize(encoder: Encoder, value: MavenVersion) {
            encoder.encodeString(value.asString)
        }

    }

    companion object {
        private const val SNAPSHOT = "-SNAPSHOT"

        fun isVersion(text: String): Boolean {
            val separator = text.lastIndexOf('-')
            val withoutPrefix = if (separator != -1) {
                text.substring(0, separator)
            } else {
                text
            }
            val elements = withoutPrefix.split('.')
            if (elements.size==1 && withoutPrefix.toIntOrNull()!=null){
                return true
            }
            if (elements.size < 2) {
                return false
            }
            for (i in 0 until elements.size - 1) {
                elements[i].toIntOrNull() ?: return false
            }
            return true

//            val items = text.split('.')
//
//
//            for (i in 0 until items.size - 1) {
//                items[i].toIntOrNull() ?: return false
//            }
//            val lastElement = items.last()
//            val separator = lastElement.indexOf('-')
//            if (separator != -1) {
//                lastElement.substring(0, separator).toIntOrNull() ?: return false
//            } else {
//                lastElement.toIntOrNull() ?: return false
//            }
//            return true
        }
    }

    val isSnapshot
        get() = raw.endsWith(SNAPSHOT)

    val elements
        get() = (if (isSnapshot) raw.substring(SNAPSHOT.length) else raw).splitToSequence('.')

    val asString
        get() = raw

    override fun compareTo(other: MavenVersion): Int {
        val self = elements.map { it.toIntOrNull() ?: 0 }.toList()
        val other = other.elements.map { it.toIntOrNull() ?: 0 }.toList()
        for (i in maxOf(other.size, self.size) - 1 downTo 0) {
            val r = self.getOrElse(i) { 0 }.compareTo(other.getOrElse(i) { 0 })
            if (r != 0) {
                return r
            }
        }
        return 0
    }
}