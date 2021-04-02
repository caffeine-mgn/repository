package pw.binom.repo.blob

import kotlinx.serialization.Serializable
import pw.binom.*
import pw.binom.concurrency.*
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.io.*
import pw.binom.io.file.*
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.execute
import pw.binom.network.network
import pw.binom.repo.repositories.docker.calcSha256
import pw.binom.repo.toHex

class FileBlobStorageService(
    val blobPath: File,
    override val id: UUID,
    val bufferSize: Int,
    override val index: BlobSQLiteDB,
) : BlobStorageService {
    companion object {
        suspend fun open(id: UUID, bufferSize: Int, root: File): FileBlobStorageService {
            if (!root.isExist) {
                throw IllegalArgumentException("Path \"${root.path}\" is not exist")
            }
            if (!root.isDirectory) {
                throw IllegalArgumentException("Path \"${root.path}\" is not direction")
            }

            return FileBlobStorageService(
                blobPath = root.relative("data"),
                id = id,
                bufferSize = bufferSize,
                index = BlobSQLiteDB.open(root.relative("index.db"))
            )
        }
    }

    private val logger = Logger.getLogger("FileBlobStorageService ${blobPath.parent!!.path}")
    private val executor = WorkerPool(10)

    init {
        blobPath.mkdirs()
        logger.info("Start Storage")
    }

    override suspend fun getData(id: UUID, output: AsyncOutput): Boolean {
        val file = blobPath.relative(id.toString())
        if (!file.isFile) {
            return false
        }
        ByteBuffer.alloc(bufferSize).use { buffer ->
            val ch = file.read()
            while (true) {
                buffer.clear()
                val size = execute(executor) {
                    ch.read(buffer)
                }
                if (size == 0) {
                    break
                }
                buffer.flip()
                while (buffer.remaining > 0) {
                    output.write(buffer)
                }
            }
        }
        return true
    }

    override suspend fun calcDigest(id: UUID): ByteArray? {
        val file = blobPath.relative(id.toString())
        if (!file.isFile) {
            return null
        }
        val sha = Sha256MessageDigest()
        return execute(executor) {
            ByteBuffer.alloc(bufferSize).use { buf ->
                file.read().use {
                    while (true) {
                        buf.clear()
                        val l = it.read(buf)
                        if (l == 0) {
                            break
                        }
                        buf.flip()
                        sha.update(buf)
                    }
                }
            }
            sha.finish()
        }
    }

    override suspend fun getSize(id: UUID): ULong? {
        val file = blobPath.relative(id.toString())
        return execute(executor) {
            if (file.isFile) {
                file.size.toULong()
            } else {
                null
            }
        }
    }

    override suspend fun store(id: UUID, append: Boolean, input: AsyncInput): Long {
        val file = blobPath.relative(id.toString())
        val ch = file.write(append)
        ByteBuffer.alloc(bufferSize).use { buffer ->
            while (true) {
                buffer.clear()
                if (input.read(buffer) == 0) {
                    break
                }
                buffer.flip()
                ch.writeFully(buffer)
            }
        }
        return execute(executor) {
            file.size
        }
    }

    override suspend fun isCanStore(size: Long): Boolean =
        execute(executor) {
            blobPath.availableSpace > size
        }

    override suspend fun asyncClose() {
        index.asyncClose()
        executor.shutdown()
    }
}