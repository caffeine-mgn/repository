package pw.binom.repo.repositories.maven

import pw.binom.UUID
import pw.binom.date.Date
import pw.binom.db.async.pool.*
import pw.binom.db.sqlite.AsyncSQLiteConnector
import pw.binom.io.AsyncCloseable
import pw.binom.io.file.File

private const val FILES = "files"
private const val REPOSITORY = "repository"
private const val GROUP = "`group`"
private const val ARTIFACT = "artifact"
private const val VERSION = "version"
private const val NAME = "name"
private const val BLOB_ID = "blob_id"
private const val STORAGE_ID = "storage_id"
private const val CREATED = "created"
private const val UPDATED = "updated"


private val SELECT_BLOB_WITH_VERSION = SelectQuery(
    """
        select $STORAGE_ID as storage, $BLOB_ID as blob
        from $FILES
        where $REPOSITORY=:repository and $GROUP = :group and $ARTIFACT = :artifact and $NAME = :name and $VERSION = :version
        limit 1
    """
).mapper {
    BlobPtr(
        storage = it.getUUID("storage")!!,
        id = it.getUUID("blob")!!
    )
}

private val DELETE_WITH_VERSION = UpdateQuery(
    """
        delete from $FILES
        where $REPOSITORY=:repository and $GROUP = :group and $ARTIFACT = :artifact and $NAME = :name and $VERSION = :version
    """
)

private val DELETE_WITHOUT_VERSION = UpdateQuery(
    """
        delete from $FILES
        where $REPOSITORY=:repository and $GROUP = :group and $ARTIFACT = :artifact and $NAME = :name and $VERSION is null
    """
)

private val SELECT_BLOB_WITHOUT_VERSION = SelectQuery(
    """
        select $STORAGE_ID as storage, $BLOB_ID as blob
        from $FILES
        where $REPOSITORY=:repository and $GROUP = :group and $ARTIFACT = :artifact and $NAME = :name
        limit 1
    """
).mapper {
    BlobPtr(
        storage = it.getUUID("storage")!!,
        id = it.getUUID("blob")!!
    )
}

private val UPSERT = UpdateQuery(
    """
insert into $FILES ($REPOSITORY,$GROUP,$ARTIFACT,$VERSION,$NAME,$CREATED,$UPDATED,$STORAGE_ID,$BLOB_ID) values(:repository,:group,:artifact,:version,:name,:now,:now,:storage,:blob)
on conflict($REPOSITORY,$GROUP,$ARTIFACT,$VERSION,$NAME) do update set $BLOB_ID=:blob, $STORAGE_ID=:storage, $UPDATED=:now
"""
)

class BlobPtr(val storage: UUID, val id: UUID)

class MavenIndexer2 private constructor(
    val pool: AsyncConnectionPool,
    val repositoryName: String,
    val closePool: Boolean,
) : AsyncCloseable {
    companion object {

        suspend fun create(
            repositoryName: String,
            pool: AsyncConnectionPool,
            closePool: Boolean = false
        ): MavenIndexer2 {
            pool.borrow {
                executeUpdate(
                    """
                        create table if not exists $FILES(
                            $REPOSITORY text not null,
                            $GROUP text not null,
                            $ARTIFACT text not null,
                            $VERSION text null,
                            $NAME text not null,
                            $CREATED int8 not null,
                            $UPDATED int8 not null,
                            $BLOB_ID blob not null,
                            $STORAGE_ID blob not null
                        );
                        CREATE UNIQUE INDEX if not exists full_path on $FILES ($REPOSITORY,$GROUP,$ARTIFACT,$VERSION,$NAME);
                """
                )
                commit()
            }
            return MavenIndexer2(
                pool = pool,
                repositoryName = repositoryName,
                closePool = closePool,
            )
        }

        suspend fun create(repositoryName: String, file: File): MavenIndexer2 {
            val pool = AsyncConnectionPool.create(
                maxConnections = 1,
                factory = { AsyncSQLiteConnector.openFile(file) }
            )
            return create(repositoryName = repositoryName, pool = pool as AsyncConnectionPool, closePool = true)
        }
    }

    override suspend fun asyncClose() {
        if (closePool) {
            pool.asyncClose()
        }
    }

    suspend fun delete(ptr: MavenPtr) {
        pool.borrow {
            if (ptr.version != null) {
                execute(
                    DELETE_WITH_VERSION,
                    "repository" to repositoryName,
                    "group" to ptr.group,
                    "artifact" to ptr.artifact,
                    "version" to ptr.version,
                    "name" to ptr.name,
                )
            } else {
                execute(
                    DELETE_WITHOUT_VERSION,
                    "repository" to repositoryName,
                    "group" to ptr.group,
                    "artifact" to ptr.artifact,
                    "name" to ptr.name,
                )
            }
            commit()
        }
    }

    suspend fun upsert(ptr: MavenPtr, blob: UUID, storage: UUID) {
        pool.borrow {
            execute(
                UPSERT,
                "repository" to repositoryName,
                "group" to ptr.group,
                "artifact" to ptr.artifact,
                "version" to ptr.version,
                "name" to ptr.name,
                "blob" to blob,
                "storage" to storage,
                "now" to Date(),
            )
            commit()
        }
    }

    suspend fun find(ptr: MavenPtr) =
        pool.borrow {
            if (ptr.version != null) {
                selectOneOrNull(
                    SELECT_BLOB_WITH_VERSION,
                    "repository" to repositoryName,
                    "group" to ptr.group,
                    "artifact" to ptr.artifact,
                    "name" to ptr.name,
                    "version" to ptr.version,
                )
            } else {
                selectOneOrNull(
                    SELECT_BLOB_WITHOUT_VERSION,
                    "repository" to repositoryName,
                    "group" to ptr.group,
                    "artifact" to ptr.artifact,
                    "name" to ptr.name,
                )
            }
        }
}
/*

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
}*/
