package pw.binom.repo.blob

import pw.binom.UUID
import pw.binom.concurrency.Worker
import pw.binom.concurrency.asReference
import pw.binom.date.Date
import pw.binom.db.ResultSet
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.db.sync.SyncConnection
import pw.binom.db.sync.firstOrNull
import pw.binom.io.file.File
import pw.binom.io.use

//private const val COLUMNS = "id, digest, size, mimetype, upload_date, last_usage_date"
//private val mapper: (ResultSet) -> BlobIndex.Blob = {
//    BlobIndex.Blob(
//        id = it.getUUID(0)!!,
//        digest = it.getBlob(1)!!,
//        size = it.getLong(2)!!,
//        mimeType = it.getString(3),
//        uploadDate = it.getDate(4)!!,
//        lastUsageDate = it.getDate(5)!!
//    )
//}
//
//class BlobSQLiteDB private constructor(
//    connection: SyncConnection,
//    val worker: Worker
//) : BlobIndex {
//
//    companion object {
//        suspend fun open(file: File): BlobSQLiteDB {
//            val worker = Worker()
//            return execute(worker) {
//                val connection = SQLiteConnector.openFile(file)
//                BlobSQLiteDB(
//                    connection = connection,
//                    worker = worker,
//                )
//            }
//        }
//    }
//
//    init {
//        connection.createStatement().use {
//            it.executeUpdate(
//                """
//                    create table if not exists blobs (
//                        id blob PRIMARY KEY not null,
//                        digest BLOB not null,
//                        size integer(8) not null,
//                        mimetype text null,
//                        upload_date integer(8) not null,
//                        last_usage_date integer(8) not null
//                    );
//                    """
//            )
//        }
//        connection.commit()
//    }
//
//    private val connection = connection.asReference()
//    private val insert = connection.prepareStatement(
//        """
//        insert into blobs ($COLUMNS) values(?, ?, ?, ?, ?, ?)
//    """
//    ).asReference()
//
//    private val findByDigest = connection.prepareStatement(
//        """
//        select $COLUMNS from blobs where digest = ? limit 1
//    """
//    ).asReference()
//
//    private val findById = connection.prepareStatement(
//        """
//        select $COLUMNS from blobs where id = ? limit 1
//    """
//    ).asReference()
//
//    private val updateMimeType = connection.prepareStatement(
//        """
//        update blobs set mimetype = ? where id = ?
//    """
//    ).asReference()
//
//    private val updateLastUsage = connection.prepareStatement(
//        """
//        update blobs set last_usage_date = ? where id = ?
//    """
//    ).asReference()
//
//    override suspend fun insert(blob: BlobIndex.Blob): Boolean =
//        execute(worker) {
//            val r = insert.value.executeUpdate(
//                blob.id, blob.digest, blob.size, blob.mimeType, blob.uploadDate, blob.lastUsageDate
//            ) > 0
//            connection.value.commit()
//            r
//        }
//
//    override suspend fun findByDigest(digest: ByteArray): BlobIndex.Blob? =
//        execute(worker) {
//            findByDigest.value.executeQuery(digest).firstOrNull(mapper)
//        }
//
//    override suspend fun findById(id: UUID): BlobIndex.Blob? =
//        execute(worker) {
//            findById.value.executeQuery(id).firstOrNull(mapper)
//        }
//
//    override suspend fun updateContentType(id: UUID, mimeType: String?): Boolean =
//        execute(worker) {
//            updateMimeType.value.executeUpdate(mimeType, id) > 0
//        }
//
//    override suspend fun updateLastUsageDate(id: UUID, date: Date): Boolean =
//        execute(worker) {
//            updateLastUsage.value.executeUpdate(date, id) > 0
//        }
//
//    override suspend fun asyncClose() {
//        execute(worker) {
//            insert.value.close()
//            insert.close()
//            findByDigest.value.close()
//            findByDigest.close()
//            findById.value.close()
//            findById.close()
//            updateMimeType.value.close()
//            updateMimeType.close()
//            connection.value.close()
//            connection.close()
//        }
//    }
//}