package pw.binom.repo.repositories.docker

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pw.binom.UUID
import pw.binom.concurrency.Worker
import pw.binom.concurrency.execute
import pw.binom.db.async.pool.AsyncConnectionPool
import pw.binom.db.serialization.*
import pw.binom.db.sqlite.AsyncSQLiteConnector
import pw.binom.io.AsyncCloseable
import pw.binom.io.file.File
import pw.binom.nextUuid
import kotlin.random.Random

const val LABELS = "labels"
const val LABELS_LAYOUTS = "${LABELS}_layouts"
const val LABEL_ID = "label_id"
const val LAYOUT_DIGEST = "layout_digest"

@Serializable
@TableName(LABELS)
data class LabelEntity(
    @Contextual
    @SerialName(LABEL_ID)
    val id: UUID,
    val digest: ByteArray,
    val name: String,
    val version: String,
    val body: String,
    val size: Long,
    @SerialName("content_type")
    val contentType: String,
)

@Serializable
@TableName(LABELS_LAYOUTS)
data class LabelLayoutEntity(
    @Id
    @Contextual
    val id: UUID,

    @SerialName(LAYOUT_DIGEST)
    val layoutDigest: ByteArray,

    @Contextual
    @SerialName("storage_id")
    val storageId: UUID,

    @Contextual
    @SerialName("blob_id")
    val blobId: UUID
)

class DockerIndex(pool: AsyncConnectionPool) : AsyncCloseable {
    companion object {
        suspend fun open(file: File): DockerIndex {
            val worker = Worker()
            return execute(worker) {
                val con = AsyncConnectionPool.create(maxConnections = 1) {
                    AsyncSQLiteConnector.openFile(file)
                }
                createTables(con)
                DockerIndex(con)
            }
        }

        private suspend fun createTables(pool: AsyncConnectionPool) {
            pool.borrow {
                executeUpdate(
                    """
                    begin;
create table if not exists $LABELS (
    $LABEL_ID BLOB PRIMARY KEY not null,
    digest BLOB not null,
    name text not null,
    version text not null,
    body text not null,
    size number not null,
    content_type text not null
);
create unique index if not exists ${LABELS}_full_name ON $LABELS(name, version);

create table if not exists $LABELS_LAYOUTS (
    storage_id     blob not null,
    blob_id        blob not null,
    $LAYOUT_DIGEST BLOB not null,
    id BLOB not null
);
create unique index if not exists ${LABELS_LAYOUTS}_label ON $LABELS_LAYOUTS($LAYOUT_DIGEST);
create index if not exists ${LABELS_LAYOUTS}_layout ON $LABELS_LAYOUTS(id);
create unique index if not exists ${LABELS}_full_name ON $LABELS_LAYOUTS(id, $LAYOUT_DIGEST);
commit;
                """
                )
            }
        }
    }

    private val db = DBContext.create(pool)

    suspend fun findBlobById(uuid: UUID) =
        db.su {
            it.selectFrom<LabelLayoutEntity>("where id=:id", "id" to uuid).firstOrNull()
        }

    suspend fun findByLayoutDigest(data: ByteArray) =
        db.su {
            it.selectFrom<LabelLayoutEntity>(
                "where $LAYOUT_DIGEST=:digest limit 1",
                "digest" to data
            )
                .firstOrNull()
        }

    suspend fun findByLabelDigest(data: ByteArray) =
        db.su {
            it.selectFrom<LabelEntity>(
                "where digest=:digest limit 1",
                "digest" to data
            )
                .firstOrNull()
        }

    private suspend fun findLabel(name: String, version: String) =
        db.su {
            it.selectFrom<LabelEntity>(
                "where name=:name and version=:version limit 1",
                "name" to name,
                "version" to version
            )
                .firstOrNull()
        }

    suspend fun upsertLabel(name: String, version: String, contentType: String, data: String, digest: ByteArray) {
        db.re {
            val oldLabelId = findByLabelDigest(digest)
            if (oldLabelId != null) {
                it.deleteFrom<LabelEntity>("where $LABEL_ID=:id", "id" to oldLabelId.id)
            }
            val oldLabelId1 = findLabel(name = name, version = version)
            if (oldLabelId1 != null) {
                it.deleteFrom<LabelEntity>("where digest=:digest", "digest" to oldLabelId1.id)
            }
            it.insert(
                LabelEntity(
                    id = Random.nextUuid(),
                    digest = digest,
                    name = name,
                    version = version,
                    body = data,
                    size = data.length.toLong(),
                    contentType = contentType,
                )
            )
        }
    }

    suspend fun insertLayout(id: UUID, blobId: UUID, storageId: UUID, digest: ByteArray) {
        db.re {
            it.insert(
                LabelLayoutEntity(
                    id = id,
                    blobId = blobId,
                    storageId = storageId,
                    layoutDigest = digest,
                )
            )
        }
    }

//    suspend fun insertLayout(name: String, label: String, layouts: List<ByteArray>) {
//        db.re { con ->
//            val oldLabelId = findLabel(name = name, label = label)
//                ?: throw IllegalArgumentException("Image $name:$label not found")
//            layouts.forEach {
//                con.insert(
//                    LabelLayoutEntity(
//                        id = oldLabelId.id,
//                        layoutDigest = it
//                    )
//                )
//            }
//        }
//    }

//    class Label(val digest: ByteArray, val data: String, val size: Long)

    suspend fun isLabelExist(name: String, version: String): Boolean =
        findLabel(name = name, version = version) != null

    suspend fun getLabelByName(name: String, version: String) =
        findLabel(name = name, version = version)

    suspend fun getLabelByDigest(digest: ByteArray) =
        db.su {
            it.selectFrom<LabelEntity>("where digest=:digest limit 1", "digest" to digest).firstOrNull()
        }

    override suspend fun asyncClose() {
        db.asyncClose()
    }
}

//fun LabelEntity.toDto() =
//    DockerIndex.Label(
//        digest = digest,
//        size = size,
//        data = body,
//    )