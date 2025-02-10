package pw.binom.repo.repositories.docker

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pw.binom.db.async.pool.AsyncConnectionPool
import pw.binom.db.serialization.*
import pw.binom.db.sqlite.AsyncSQLiteConnector
import pw.binom.io.AsyncCloseable
import pw.binom.io.file.File
import pw.binom.uuid.UUID
import pw.binom.uuid.nextUuid
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
    val blobId: UUID,
)

class DockerIndex(pool: AsyncConnectionPool) : AsyncCloseable {
    companion object {
        suspend fun open(file: File): DockerIndex {
            val con = AsyncConnectionPool.create(1) {
                AsyncSQLiteConnector.openFile(file)
            }
            createTables(con)
            return DockerIndex(con)
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
        db.su2 {
            it.select(LabelLayoutEntity.serializer()) {
                """
                   select * from ${tableName<LabelLayoutEntity>()}
                    where id=${param(uuid)}
                """
            }.firstOrNull()
        }

    suspend fun findByLayoutDigest(data: ByteArray) =
        db.su2 {
            it.select(LabelLayoutEntity.serializer()) {
                """
                 select * from ${tableName<LabelLayoutEntity>()} where $LAYOUT_DIGEST=${param(data)} limit 1   
                """
            }.firstOrNull()
        }

    suspend fun findByLabelDigest(data: ByteArray) =
        db.su2 {
            it.select(LabelEntity.serializer()) {
                """
                   select * from ${tableName<LabelEntity>()}
                   where digest=${param(data)} limit 1
                """
            }.firstOrNull()
        }

    private suspend fun findLabel(name: String, version: String) =
        db.su2 {
            it.select(LabelEntity.serializer()) {
                """
                   select * from ${tableName<LabelEntity>()}
                   where name=${param(name)} and version=${param(version)} limit 1
                """
            }.firstOrNull()
        }

    suspend fun upsertLabel(name: String, version: String, contentType: String, data: String, digest: ByteArray) {
        db.re2 {
            val oldLabelId = findByLabelDigest(digest)
            if (oldLabelId != null) {
                it.update {
                    """
                       delete from ${tableName<LabelEntity>()}
                        where $LABEL_ID=${param(oldLabelId.id)}
                    """
                }
            }
            val oldLabelId1 = findLabel(name = name, version = version)
            if (oldLabelId1 != null) {
                it.update {
                    """
                        delete from ${tableName<LabelEntity>()}
                        where digest=${param(oldLabelId1.id)}
                    """.trimIndent()
                }
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
        db.re2 {
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
        db.su2 {
            it.select(LabelEntity.serializer()) {
                """
                select * from ${tableName<LabelEntity>()}
                where digest=${param(digest)} limit 1
                """
            }//<LabelEntity>("where digest=:digest limit 1", "digest" to digest).firstOrNull()
        }.firstOrNull()

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