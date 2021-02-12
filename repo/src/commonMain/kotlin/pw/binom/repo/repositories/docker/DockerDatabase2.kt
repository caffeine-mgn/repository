package pw.binom.repo.repositories.docker

import pw.binom.concurrency.ThreadRef
import pw.binom.doFreeze
import pw.binom.io.file.File
import pw.binom.io.use
import pw.binom.repo.repositories.AbstractSQLiteService
import pw.binom.uuid
import kotlin.random.Random

const val LABELS = "labels"
const val LABELS_LAYOUTS = "${LABELS}_layouts"
const val LABEL_ID = "label_id"
const val LAYOUT_DIGEST = "layout_digest"

class DockerDatabase2(file: File) : AbstractSQLiteService(file) {

    init {
        doFreeze()
        worker.execute(Unit) {
            try {
                connection.createStatement().use { st ->
                    st.executeUpdate(
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
                    connection.commit()
                }
            } catch (e: Throwable) {
                connection.rollback()
                e.printStackTrace()
            }
        }
    }

    suspend fun upsertLabel(name: String, label: String, data: String) {
        executeOnWorker {
            try {
                val oldLabelId =
                    connection.prepareStatement("select $LABEL_ID from $LABELS where name = ? and label=? limit 1")
                        .use {
                            it.set(0, name)
                            it.set(1, label)
                            it.executeQuery().map { it.getUUID(0) }.asSequence().firstOrNull()
                        }
                if (oldLabelId != null) {
                    connection.prepareStatement("delete from $LABELS where $LABEL_ID = ?").use {
                        it.set(0, oldLabelId)
                        it.executeUpdate()
                    }
                    connection.prepareStatement("delete from $LABELS_LAYOUTS where $LABEL_ID = ?").use {
                        it.set(0, oldLabelId)
                        it.executeUpdate()
                    }
                }
                connection.prepareStatement("insert into $LABELS ($LABEL_ID, digest, name, label, body, size) values (?, ?, ?, ?, ?, ?)")
                    .use {
                        it.set(0, Random.uuid())
                        it.set(1, data.calcSha256())
                        it.set(2, name)
                        it.set(3, label)
                        it.set(4, data)
                        it.set(5, data.length)
                        it.executeUpdate()
                    }
                connection.commit()
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            }
        }
    }

    suspend fun insertLayout(name: String, label: String, layouts: List<ByteArray>) {
        executeOnWorker {
            try {
                val oldLabelId =
                    connection.prepareStatement("select $LABEL_ID from $LABELS where name = ? and label=? limit 1")
                        .use {
                            it.set(0, name)
                            it.set(1, label)
                            it.executeQuery().map { it.getUUID(0) }.asSequence().firstOrNull()
                        } ?: throw IllegalArgumentException("Image $name:$label not found")
                connection.prepareStatement("insert into $LABELS_LAYOUTS ($LABEL_ID, $LAYOUT_DIGEST) values(?, ?)")
                    .use {
                        it.set(0, oldLabelId)
                        layouts.forEach { d ->
                            it.set(1, d)
                            it.executeUpdate()
                        }
                    }
                connection.commit()
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            }
        }
    }

    class Label(val digest: ByteArray, val data: String, val size: Long)

    suspend fun isLabelExist(name: String, label: String): Boolean = executeOnWorker {
        connection.prepareStatement("select $LABEL_ID from $LABELS where name=? and label=? limit 1").use {
            it.set(0, name)
            it.set(1, label)
            it.executeQuery().use { it.next() }
        }
    }

    suspend fun getLabelByName(name: String, label: String) = executeOnWorker {

        connection.prepareStatement("select digest, size, body from $LABELS where name=? and label=? limit 1").use {
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

    suspend fun getLabelByDigest(digest: ByteArray) = executeOnWorker {
        connection.prepareStatement("select size, body from $LABELS where digest = ? limit 1").use {
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
}