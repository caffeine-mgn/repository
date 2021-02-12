package pw.binom.repo.repositories

import pw.binom.*

interface BlobStorageService {
    val id: UUID

    class Blob(val size: Long, val contentType: String)

    suspend fun storeById(id: UUID, contentType: String, append: Boolean, input: AsyncInput): Blob
    suspend fun getById(id: UUID, output: AsyncOutput): Boolean
    suspend fun getByDigest(digest: ByteArray, output: AsyncOutput): Boolean

    //    suspend fun storeBlog(digest: ByteArray, contentType: String): Output
    suspend fun updateContentType(digest: ByteArray, contentType: String): Boolean

    suspend fun getBlobById(id: UUID): Blob?
    suspend fun getBlobByDigest(digest: ByteArray): Blob?

    //    suspend fun getBlobData(digest: ByteArray, output: AsyncOutput): Boolean
    suspend fun isExist(digest: ByteArray): Boolean

    suspend fun isCanStore(size: Long):Boolean
}