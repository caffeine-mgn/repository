package pw.binom.repo.blob

import pw.binom.UUID
import pw.binom.date.Date
import pw.binom.io.AsyncCloseable

interface BlobIndex : AsyncCloseable {
    data class Blob(
        val id: UUID,
        val digest: ByteArray,
        val size: Long,
        val mimeType: String?,
        val uploadDate: Date,
        val lastUsageDate: Date
    )

    suspend fun insert(blob: Blob): Boolean
    suspend fun findByDigest(digest: ByteArray): Blob?
    suspend fun findById(id: UUID): Blob?
    suspend fun updateContentType(id: UUID, mimeType: String?): Boolean
    suspend fun updateLastUsageDate(id: UUID, date: Date): Boolean
}