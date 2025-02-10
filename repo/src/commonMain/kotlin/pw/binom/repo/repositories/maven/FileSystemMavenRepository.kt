package pw.binom.repo.repositories.maven

import pw.binom.asyncInput
import pw.binom.asyncOutput
import pw.binom.copyTo
import pw.binom.date.DateTime
import pw.binom.io.AsyncInput
import pw.binom.io.DataTransferSize
import pw.binom.io.file.*
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn

class FileSystemMavenRepository(
    id: String,
    private val root: File,
    override val readOnly: Boolean,
) : MavenRepository {
    private val logger = Logger.getLogger("FileSystemMavenRepository $id")
    override suspend fun isExist(group: MavenGroup, artifact: String, version: MavenVersion, file: String): Long? =
        group.resolve(root)
            .relative(artifact)
            .relative(version.asString)
            .relative(file)
            .takeIfFile()
            ?.size

    override suspend fun get(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
    ): AsyncInput? {
        val stream = group.resolve(root)
            .relative(artifact)
            .relative(version.asString)
            .relative(file)
            .takeIfFile()
            ?.openRead()
            ?.asyncInput()
        if (stream != null) {
            logger.info("Returning ${group.asString}:$artifact:${version.asString}")
        }
        return stream
    }

    override suspend fun push(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
        from: AsyncInput,
    ) {
        if (readOnly) {
            TODO()
        }
        val dir = group.resolve(root).relative(artifact).relative(version.asString)
        dir.mkdirs()
        val targetFile = dir.relative(file)
        try {
            targetFile.openWrite().use { output ->
                from.copyTo(output.asyncOutput())
            }
            logger.info(text = "Success pushed ${group.asString}:$artifact:${version.asString}")
        } catch (e: Throwable) {
            logger.warn(text = "Can't push to ${group.asString}:$artifact:${version.asString}", exception = e)
            targetFile.delete()
            throw e
        }
    }

    override suspend fun getMetaData(group: MavenGroup, artifact: String): MetaData? {
        val direction = group.resolve(root)
            .takeIfDirection()
            ?: return null
        val versions = direction
            .list()
            .filter { it.isDirectory }.map { MavenVersion(it.name) }
        val last = versions.maxOrNull()
        val lastRelease = versions.asSequence().filter { !it.isSnapshot }.maxOrNull()
        return MetaData(
            groupId = group,
            artifactId = artifact,
            version = last,
            versions = versions,
            lastUpdate = last?.let { direction.relative(it.asString) }
                ?.takeIfDirection()
                ?.lastModified?.let { DateTime(it) }
                ?: DateTime.now,
            latest = last,
            release = lastRelease,
        )
    }
}