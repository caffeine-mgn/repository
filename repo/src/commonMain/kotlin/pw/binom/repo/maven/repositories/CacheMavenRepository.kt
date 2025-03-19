package pw.binom.repo.maven.repositories

import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.io.AsyncInput
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.repo.maven.MavenVersion
import pw.binom.repo.repositories.maven.MavenGroup
import kotlin.time.Duration

data class CacheMavenRepository(
    private val id: String,
    private val source: MavenRepository,
    private val cache: MavenRepository,
    override val readOnly: Boolean,
    val copingTimeout: Duration,
) : MavenRepository {
    override fun toString(): String =
        "CacheMavenRepository(id=$id,copingTimeout=$copingTimeout,source=$source,cache=$cache)"

    private val logger = Logger.getLogger("CacheMavenRepository $id")
    override suspend fun get(group: MavenGroup, artifact: String, version: MavenVersion, file: String): AsyncInput? {
        if (version.isSnapshot) {
            return source.get(
                group = group,
                artifact = artifact,
                version = version,
                file = file,
            )
        }
        val existStream = cache.get(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        )
        if (existStream != null) {
            return existStream
        }
        val new = source.get(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        ) ?: return null
        logger.info("Saving ${group.asString}:$artifact:${version.asString} to cache")
        val okSuccess = withTimeoutOrNull(copingTimeout) {
            new.useAsync { stream ->
                cache.push(
                    group = group,
                    artifact = artifact,
                    version = version,
                    file = file,
                    from = stream,
                )
            }
            logger.info("${group.asString}:$artifact:${version.asString} Saved to cache successfully")
        } != null
        return cache.get(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        )
    }

    override suspend fun isExist(group: MavenGroup, artifact: String, version: MavenVersion, file: String): Long? {
        logger.info("Check exist $group:$artifact:$version/$file...")
        val storageSize = cache.isExist(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        )
        if (storageSize != null) {
            logger.info("File exist!")
            return storageSize
        }
        logger.info("File not exist! Check in source $source")
        return source.isExist(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        )
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
        cache.push(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
            from = from,
        )
    }

    override suspend fun getMetaData(group: MavenGroup, artifact: String) =
        source.getMetaData(group = group, artifact = artifact)

    override suspend fun getMetaData(group: MavenGroup, artifact: String, version: MavenVersion) =
        source.getMetaData(group = group, artifact = artifact, version = version)

    override suspend fun getMetaDataMd5(group: MavenGroup, artifact: String) =
        source.getMetaDataMd5(group, artifact)

    override suspend fun getMetaDataSha1(group: MavenGroup, artifact: String) =
        source.getMetaDataSha1(group, artifact)

    override suspend fun getMetaDataText(group: MavenGroup, artifact: String) =
        source.getMetaDataText(group, artifact)
}