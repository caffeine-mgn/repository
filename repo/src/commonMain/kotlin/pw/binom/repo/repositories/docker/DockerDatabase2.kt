package pw.binom.repo.repositories.docker

import pw.binom.UUID
import pw.binom.concurrency.Worker
import pw.binom.concurrency.asReference
import pw.binom.concurrency.execute
import pw.binom.db.sqlite.SQLiteConnector
import pw.binom.doFreeze
import pw.binom.io.AsyncCloseable
import pw.binom.io.file.File
import pw.binom.io.use
import pw.binom.repo.blob.BlobIndex
import pw.binom.uuid
import kotlin.random.Random

const val LABELS = "labels"
const val LABELS_LAYOUTS = "${LABELS}_layouts"
const val LABEL_ID = "label_id"
const val LAYOUT_DIGEST = "layout_digest"

class DockerDatabase2(connection: SQLiteConnector, val worker: Worker) : AsyncCloseable {
    companion object {
        suspend fun open(file: File): DockerDatabase2 {
            val worker = Worker()
            return execute(worker) {
                val con = SQLiteConnector.openFile(file)
                DockerDatabase2(con, worker)
            }
        }
    }

    fun findBlobById(uuid: UUID):BlobIndex.Blob?{
        TODO()
    }

fun findByDigest(data:ByteArray): BlobIndex.Blob?{
    TODO()
}
    init {
        connection.createStatement().use {
            it.executeUpdate(
                """
create table if not exists $LABELS (
    $LABEL_ID BLOB PRIMARY KEY not null,
    digest BLOB not null,
    name text not null,
    label text not null,
    body text not null,
    size number not null
);
create unique index if not exists ${LABELS}_full_name ON $LABELS(name, label);

create table if not exists $LABELS_LAYOUTS (
    $LAYOUT_DIGEST BLOB not null,
    $LABEL_ID BLOB not null
);
create unique index if not exists ${LABELS_LAYOUTS}_label ON $LABELS_LAYOUTS($LAYOUT_DIGEST);
create index if not exists ${LABELS_LAYOUTS}_layout ON $LABELS_LAYOUTS($LABEL_ID);
create unique index if not exists ${LABELS}_full_name ON $LABELS($LABEL_ID, $LAYOUT_DIGEST);
                """
            )
        }
        connection.commit()
    }

    private val con = connection.asReference()

    suspend fun upsertLabel(name: String, label: String, data: String) {
        execute(worker) {
            try {
                val oldLabelId =
                    con.value.prepareStatement("select $LABEL_ID from $LABELS where name = ? and label=? limit 1")
                        .use {
                            it.executeQuery(name, label).map { it.getUUID(0) }.asSequence().firstOrNull()
                        }
                if (oldLabelId != null) {
                    con.value.prepareStatement("delete from $LABELS where $LABEL_ID = ?").use {
                        it.executeUpdate(oldLabelId)
                    }
                    con.value.prepareStatement("delete from $LABELS_LAYOUTS where $LABEL_ID = ?").use {
                        it.executeUpdate(oldLabelId)
                    }
                }
                con.value.prepareStatement("insert into $LABELS ($LABEL_ID, digest, name, label, body, size) values (?, ?, ?, ?, ?, ?)")
                    .use {
                        it.executeUpdate(Random.uuid(), data.calcSha256(), name, label, data, data.length)
                    }
                con.value.commit()
            } catch (e: Throwable) {
                con.value.rollback()
                throw e
            }
        }
    }

    suspend fun insertLayout(name: String, label: String, layouts: List<ByteArray>) {
        execute(worker) {
            try {
                val oldLabelId =
                    con.value.prepareStatement("select $LABEL_ID from $LABELS where name = ? and label=? limit 1")
                        .use {
                            it.set(0, name)
                            it.set(1, label)
                            it.executeQuery().map { it.getUUID(0) }.asSequence().firstOrNull()
                        } ?: throw IllegalArgumentException("Image $name:$label not found")
                con.value.prepareStatement("insert into $LABELS_LAYOUTS ($LABEL_ID, $LAYOUT_DIGEST) values(?, ?)")
                    .use {
                        it.set(0, oldLabelId)
                        layouts.forEach { d ->
                            it.set(1, d)
                            it.executeUpdate()
                        }
                    }
                con.value.commit()
            } catch (e: Throwable) {
                con.value.rollback()
                throw e
            }
        }
    }

    class Label(val digest: ByteArray, val data: String, val size: Long)

    suspend fun isLabelExist(name: String, label: String): Boolean = execute(worker) {
        con.value.prepareStatement("select $LABEL_ID from $LABELS where name=? and label=? limit 1").use {
            it.set(0, name)
            it.set(1, label)
            it.executeQuery().use { it.next() }
        }
    }

    suspend fun getLabelByName(name: String, label: String) = execute(worker) {
        con.value.prepareStatement("select digest, size, body from $LABELS where name=? and label=? limit 1").use {
            it.set(0, name)
            it.set(1, label)
            it.executeQuery().map {
                Label(
                    digest = it.getBlob(0)!!,
                    size = it.getLong(1)!!,
                    data = it.getString(2)!!
                )
            }.asSequence().toList().firstOrNull()
        }
    }

    suspend fun getLabelByDigest(digest: ByteArray) = execute(worker) {
        con.value.prepareStatement("select size, body from $LABELS where digest = ? limit 1").use {
            it.set(0, digest)
            it.executeQuery().map {
                Label(
                    digest = digest,
                    size = it.getLong(0)!!,
                    data = it.getString(1)!!
                )
            }.asSequence().toList().firstOrNull()
        }
    }

    override suspend fun asyncClose() {
        execute(worker) {
            con.value.close()
            con.close()
        }
    }
}