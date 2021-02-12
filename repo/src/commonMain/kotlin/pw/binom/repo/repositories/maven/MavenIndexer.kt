package pw.binom.repo.repositories.maven

import pw.binom.UUID
import pw.binom.concurrency.asReference
import pw.binom.io.file.File
import pw.binom.io.use
import pw.binom.network.execute
import pw.binom.repo.repositories.AbstractSQLiteService

private const val FILES = "files"
private const val GROUP = "`group`"
private const val ARTIFACT = "artifact"
private const val VERSION = "version"
private const val NAME = "name"
private const val BLOB_ID = "blob_id"

class MavenIndexer(file: File) : AbstractSQLiteService(file) {
    init {
        worker.execute(Unit) {
            try {
                connection.createStatement().use {
                    it.executeUpdate(
                        """
                        create table if not exists $FILES(
                        $GROUP text not null,
                        $ARTIFACT text not null,
                        $VERSION text null,
                        $NAME text not null,
                        $BLOB_ID blob not null
                        )
                    """
                    )
                }
                connection.commit()
            } catch (e: Throwable) {
                connection.rollback()
            }
        }
    }

    suspend fun removeByBlobId(blobId: UUID) {
        execute(worker) {
            deleteFileByBlobId.set(0, blobId)
            deleteFileByBlobId.executeUpdate()
            connection.commit()
        }
    }

    suspend fun get(
        group: String,
        artifact: String,
        name: String,
        version: String?,
    ) =
        execute(worker) {
            (if (version == null) {
                selectWithoutVersion.let {
                    it.set(0, group)
                    it.set(1, artifact)
                    it.set(2, name)
                    it.executeQuery()
                }
            } else {
                selectWithVersion.let {
                    it.set(0, group)
                    it.set(1, artifact)
                    it.set(2, name)
                    it.set(3, version)
                    it.executeQuery()
                }
            }).map { it.getUUID(0) }.asSequence().firstOrNull()
        }

    suspend fun rewrite(
        group: String,
        artifact: String,
        name: String,
        version: String?,
        contentType: String,
        blobId: UUID
    ) {
        executeOnWorker {
            val st = if (version == null) deleteFilesWithoutVersion else deleteFilesWithVersion
            st.set(0, group)
            st.set(1, artifact)
            st.set(2, name)
            if (version != null) {
                st.set(3, version)
            }
            st.executeUpdate()
            insertFile.apply {
                set(0, group)
                set(1, artifact)
                if (version == null) {
                    setNull(2)
                } else {
                    set(2, version)
                }
                set(3, name)
                set(4, blobId)
                executeUpdate()
            }
            connection.commit()
        }
    }

    override fun close() {
        deleteFileByBlobId.close()
        deleteFilesWithVersion.close()
        deleteFilesWithoutVersion.close()
        insertFile.close()
        selectWithVersion.close()
        selectWithoutVersion.close()
        super.close()
    }

    private val deleteFileByBlobId = connection.prepareStatement(
        """
        delete from $FILES where $BLOB_ID=?
    """
    )

    private val deleteFilesWithVersion = connection.prepareStatement(
        """
        delete from $FILES
        where $GROUP = ? and $ARTIFACT = ? and $NAME = ? and $VERSION = ?
        """
    )

    private val deleteFilesWithoutVersion = connection.prepareStatement(
        """
        delete from $FILES
        where $GROUP = ? and $ARTIFACT = ? and $NAME = ? and $VERSION is null
        """
    )

    private val insertFile = connection.prepareStatement(
        """
        insert into $FILES ($GROUP, $ARTIFACT, $VERSION, $NAME, $BLOB_ID) values (?,?,?,?,?)
    """
    )

    private val selectWithVersion = connection.prepareStatement(
        """
select $BLOB_ID from $FILES where $GROUP = ? and $ARTIFACT = ? and $NAME = ? and $VERSION = ? limit 1
        """
    )

    private val selectWithoutVersion = connection.prepareStatement(
        """
select $BLOB_ID from $FILES where $GROUP = ? and $ARTIFACT = ? and $NAME = ? limit 1
        """
    )
}