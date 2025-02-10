package pw.binom.repo.repositories.maven

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pw.binom.date.DateTime
import pw.binom.db.async.pool.AsyncConnectionPool
import pw.binom.db.serialization.*
import pw.binom.db.sqlite.AsyncSQLiteConnector
import pw.binom.io.AsyncCloseable
import pw.binom.io.file.File
import pw.binom.uuid.UUID

private const val FILES = "files"
private const val GROUP = "group"
private const val ARTIFACT = "artifact"
private const val VERSION = "version"
private const val NAME = "name"
private const val BLOB_ID = "blob_id"
private const val STORAGE_ID = "storage_id"
private const val CREATED = "created"
private const val UPDATED = "updated"


@Serializable
@TableName(FILES)
data class FileEntity(
    @UseQuotes
    @SerialName(GROUP)
    val group: String,
    @SerialName(ARTIFACT)
    val artifact: String,
    @SerialName(VERSION)
    val version: String?,
    @SerialName(NAME)
    val name: String,

    @SerialName(CREATED)
    @Contextual
    val created: DateTime,
    @Contextual
    @SerialName(UPDATED)
    val updated: DateTime,
    @Contextual
    @SerialName(BLOB_ID)
    val blobId: UUID,
    @Contextual
    @SerialName(STORAGE_ID)
    val storageId: UUID,
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
            closePool: Boolean = false,
        ): MavenIndexer2 {

            pool.borrow {
                executeUpdate(
                    """
                        create table if not exists $FILES(
                            "$GROUP" text not null,
                            $ARTIFACT text not null,
                            $VERSION text null,
                            $NAME text not null,
                            $CREATED int8 not null,
                            $UPDATED int8 not null,
                            $BLOB_ID blob not null,
                            $STORAGE_ID blob not null
                        );
                        CREATE UNIQUE INDEX if not exists full_path on $FILES ("$GROUP",$ARTIFACT,$VERSION,$NAME);
                """
                )
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

    private val db = DBContext.create(pool)
    override suspend fun asyncClose() {
        if (closePool) {
            pool.asyncClose()
        }
    }

    suspend fun delete(ptr: MavenPtr) {
        db.re {
            val sb = StringBuilder()
            sb.append("delete from ")
                .append(FileEntity.serializer().tableName)
                .append(" where \"group\"=:group and artifact=:artifact and name=:name")
            if (ptr.version != null) {
                sb.append(" and version=:version")
            }
            it.update(
                sb.toString(),
                "group" to ptr.group,
                "artifact" to ptr.artifact,
                "name" to ptr.name,
                "version" to ptr.version
            )
        }
    }

    suspend fun insert(ptr: MavenPtr, blob: UUID, storage: UUID) {
        db.re2 {
            it.insert(
                FileEntity(
                    group = ptr.group,
                    artifact = ptr.artifact,
                    version = ptr.version,
                    name = ptr.name,
                    created = DateTime.now,
                    updated = DateTime.now,
                    blobId = blob,
                    storageId = storage,
                ),
            )
        }
    }

    suspend fun find(ptr: MavenPtr) =
        db.su2 {
            it.select(FileEntity.serializer()) {
                """
                   where "group"=${param(ptr.group)}
                    and artifact=${param(ptr.artifact)}
                    and name=${param(ptr.name)}
                    and version ${if (ptr.version == null) "is null" else "=${param(ptr.version)}"} 
                """
            }.map {
                BlobPtr(
                    storage = it.storageId,
                    id = it.blobId
                )
            }.firstOrNull()
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
