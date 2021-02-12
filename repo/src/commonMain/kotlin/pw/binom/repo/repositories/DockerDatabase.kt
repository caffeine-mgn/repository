package pw.binom.repo.repositories

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.modules.SerializersModule
import pw.binom.UUID
import pw.binom.io.Closeable
import pw.binom.repo.toHex

interface DockerDatabase : Closeable {
//    class Label(val image: String, val label: String, val manifest: DockerManifest2Single)

    class Image(val label: Label, val layouts: List<Layout>)

    class Layout(val digest: ByteArray, val size: Long)
    class Label(val id: UUID, val name: String, val label: String, val manifest: ByteArray)

    suspend fun insertLayout(sha256: ByteArray, size: Long)
    suspend fun layoutToManifest(body:String)
    suspend fun updateLabel(
        name: String,
        label: String,
        selfDigest: ByteArray,
        mainLayout: ByteArray,
        layouts: List<ByteArray>
    )

    suspend fun findManifest(name: String, sha256: ByteArray): String?
    suspend fun findLabel(name: String, sha256: ByteArray): Image?
    suspend fun findLabel(name: String, label: String): Image?
}