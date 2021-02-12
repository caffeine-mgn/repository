package pw.binom.repo.repositories

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
    val root: File,
    override val id: UUID,
    val bufferSize: Int,
) : BlobStorageService, Closeable {

    private val logger = Logger.getLogger("FileBlobStorageService $root")
    private val executor = WorkerPool(10)
    private val dbWorker = Worker()

    init {
        require(root.isDirectory || !root.isExist)
        if (!root.isExist) {
            require(root.mkdirs())
        }

        logger.info("Start Storage")
    }

    private val db = SQLiteConnector.openFile(root.relative("index.db"))

    init {
        db.createStatement().use { st ->
            st.executeUpdate(
                """
                    create table if not exists files (
                        id blob PRIMARY KEY not null,
                        content_type text not null,
                        digest BLOB not null,
                        size number not null
                    );
                    """
            )
        }
        db.commit()
    }

    private fun findById(id: UUID) =
        db.prepareStatement("select size, content_type from files where id=? limit 1").use {
            it.set(0, id)
            it.executeQuery().map {
                BlobStorageService.Blob(
                    size = it.getLong(0)!!,
                    contentType = it.getString(1)!!
                )
            }.asSequence().firstOrNull()
        }

    private suspend fun storeById2(
        id: UUID,
        contentType: String,
        append: Boolean,
        inputRef: Reference<AsyncInput>
    ): BlobStorageService.Blob {
        val resultFile = execute(executor) {
            val file = root.relative(id.toString())
            val buf = ByteBuffer.alloc(bufferSize)
            try {
                file.write(append = append).use { fos ->
                    while (true) {
                        val read = network { inputRef.value.read(buf) }
                        if (read == 0) {
                            break
                        }
                        buf.flip()
                        fos.write(buf)
                    }
                }
            } finally {
                inputRef.close()
            }
            file
        }
        val digest = resultFile.calcSha256(bufferSize)
        if (db.prepareStatement("select id from files where id=? limit 1").use {
                it.set(0, id)
                it.executeQuery().map { it.getUUID(0) }.asSequence().firstOrNull()
            } == null) {
            db.prepareStatement(
                """insert into files (id, content_type, digest, size)
                        values (?,?,?,?)
                    """
            ).use {
                it.set(0, id)
                it.set(1, contentType)
                it.set(2, digest)
                it.set(3, resultFile.size)
                it.executeUpdate()
            }
        } else {
            db.prepareStatement(
                """
                        update files set content_type=?, digest=?, size=?
                        where id=?
                    """
            ).use {
                it.set(0, contentType)
                it.set(1, digest)
                it.set(2, resultFile.size)
                it.set(3, id)
                it.executeUpdate()
            }
        }
        db.commit()
        return BlobStorageService.Blob(
            size = resultFile.size,
            contentType = contentType
        )
    }

    override suspend fun storeById(
        id: UUID,
        contentType: String,
        append: Boolean,
        input: AsyncInput
    ): BlobStorageService.Blob = input.useReference { inputRef ->
        execute(dbWorker) {
            storeById2(
                id = id,
                contentType = contentType,
                append = append,
                inputRef = inputRef
            )
        }
    }

    override suspend fun getById(id: UUID, output: AsyncOutput): Boolean {
        val exist = execute(dbWorker) {
            db.prepareStatement("select id from files where id=? limit 1").use {
                it.set(0, id)
                it.executeQuery().map { it.getUUID(0) }.asSequence().any()
            }
        }
        if (!exist) {
            logger.info("Can't find file $id")
            return false
        }
        return sendFile(id, output)
    }

    private suspend fun sendFile(id: UUID, output: AsyncOutput): Boolean =
        output.useReference { outRef ->
            execute(executor) {
                val file = root.relative(id.toString())
                if (!file.isFile) {
                    logger.warn("File $file not found")
                    return@execute false
                }
                ByteBuffer.alloc(bufferSize) { buf ->
                    file.read().use { fio ->
                        while (true) {
                            buf.clear()
                            val read = fio.read(buf)
                            if (read == 0) {
                                break
                            }
                            buf.flip()
                            network { outRef.value.write(buf) }
                        }
                    }
                }
                network { outRef.value.flush() }
                true
            }
        }

    override suspend fun getByDigest(digest: ByteArray, output: AsyncOutput): Boolean {
        val id = execute(dbWorker) {
            db.prepareStatement("select id from files where digest=? limit 1").use {
                it.set(0, digest)
                it.executeQuery().map { it.getUUID(0) }.asSequence().toList().firstOrNull()
            }
        } ?: return false
        return sendFile(id, output)
    }

//    override suspend fun storeBlog(digest: ByteArray, contentType: String): Output {
//        val data = root.relative(digest.toHex())
//        val meta = root.relative("${digest.toHex()}.meta")
//        data.delete()
//        meta.delete()
//        meta.write().bufferedAsciiWriter()
//            .use {
//                it.append(Json.encodeToString(BlobInfo.serializer(), BlobInfo(contentType)))
//            }
//        return data.write()
//    }

    override suspend fun updateContentType(digest: ByteArray, contentType: String): Boolean =
        execute(dbWorker) {
            val id = db.prepareStatement("select id from files where digest=? limit 1").use {
                it.set(0, digest)
                it.executeQuery().map { it.getUUID(0) }.asSequence().firstOrNull()
            } ?: return@execute false
            db.prepareStatement("update files set content_type=? where id=?").use {
                it.set(0, contentType)
                it.set(1, id)
                it.executeUpdate()
            }
            db.commit()
            return@execute true
        }

    override suspend fun getBlobById(id: UUID): BlobStorageService.Blob? =
        execute(dbWorker) {
            db.prepareStatement("select size, content_type from files where id=? limit 1").use {
                it.set(0, id)
                it.executeQuery().map {
                    BlobStorageService.Blob(
                        size = it.getLong(0)!!,
                        contentType = it.getString(1)!!
                    )
                }.asSequence().firstOrNull()
            }
        }

    override suspend fun getBlobByDigest(digest: ByteArray): BlobStorageService.Blob? =
        execute(dbWorker) {
            db.prepareStatement("select size, content_type from files where digest=? limit 1").use {
                it.set(0, digest)
                it.executeQuery().map {
                    BlobStorageService.Blob(
                        size = it.getLong(0)!!,
                        contentType = it.getString(1)!!
                    )
                }.asSequence().firstOrNull()
            }
        }

    override suspend fun isExist(digest: ByteArray): Boolean = root.relative(digest.toHex()).isFile
    override suspend fun isCanStore(size: Long): Boolean =
        execute(executor) {
            root.availableSpace > size
        }

    override fun close() {
        db.close()
    }
}

@Serializable
private class BlobInfo(val contentType: String)