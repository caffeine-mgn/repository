package pw.binom.repo.blob

import pw.binom.*
import pw.binom.io.AsyncCloseable
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.uuid.UUID

interface BlobStorageService : AsyncCloseable {
    val id: UUID
    val remaining: Long

    suspend fun getData(id: UUID, output: AsyncOutput): Boolean
    suspend fun calcDigest(id: UUID): ByteArray?
    suspend fun getSize(id: UUID): ULong?

    //    suspend fun getDataById(id: UUID, output: AsyncOutput): Boolean
//    suspend fun getDataByDigest(digest: ByteArray, output: AsyncOutput): Boolean
//    suspend fun updateContentType(digest: ByteArray, contentType: String): Boolean
    suspend fun store(id: UUID, append: Boolean, input: AsyncInput): Long
    suspend fun isCanStore(size: Long): Boolean
}