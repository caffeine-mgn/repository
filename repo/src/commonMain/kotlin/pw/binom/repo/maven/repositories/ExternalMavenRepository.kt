package pw.binom.repo.maven.repositories

import kotlinx.coroutines.withTimeoutOrNull
import pw.binom.SafeException
import pw.binom.io.AsyncCloseable
import pw.binom.io.AsyncInput
import pw.binom.io.http.*
import pw.binom.io.httpClient.HttpClientRunnable
import pw.binom.io.httpClient.HttpRequestBuilder
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.repo.maven.MavenVersion
import pw.binom.repo.properties.HttpAuthProperties
import pw.binom.repo.repositories.maven.MavenGroup
import pw.binom.repo.repositories.maven.MavenMetadata
import pw.binom.url.URL
import pw.binom.url.toPath
import kotlin.time.Duration

data class ExternalMavenRepository(
    private val id: String,
    val client: HttpClientRunnable,
    val url: URL,
    val auth: HttpAuthProperties?,
    val getTimeout: Duration,
    override val readOnly: Boolean,
) : MavenRepository {
    private val logger = Logger.getLogger("ExternalMavenRepository $id")
    override fun toString(): String =
        "ExternalMavenRepository(id=$id,url=$url,auth=$auth,getTimeout=$getTimeout)"

    class AutoCloseAsyncInput(val delegate: AsyncInput, val closable: AsyncCloseable) : AsyncInput by delegate {
        override suspend fun asyncClose() {
            try {
                delegate.asyncClose()
            } finally {
                closable.asyncClose()
            }
        }
    }

    private fun url(group: MavenGroup, artifact: String, version: MavenVersion, file: String) =
        url + group.asPath + artifact.toPath + version.asString.toPath + file.toPath

    private suspend fun connect(
        method: String,
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
    ): HttpRequestBuilder {
        val resultUrl = url(
            group = group,
            artifact = artifact,
            version = version,
            file = file,
        )
        logger.info("resultUrl=$resultUrl")

        val headers = if (auth != null) {
            val headerValue = when {
                auth.basicAuth != null -> BasicAuth(
                    login = auth.basicAuth.user,
                    password = auth.basicAuth.password,
                ).headerValue

                auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token).headerValue
                else -> TODO()
            }
            headersOf(
                Headers.AUTHORIZATION to headerValue
            )
        } else {
            emptyHeaders()
        }

        return client.request(
            method = method,
            url = resultUrl,
            headers = headers,
        )
    }

    override suspend fun isExist(group: MavenGroup, artifact: String, version: MavenVersion, file: String): Long? =
        withTimeoutOrNull(getTimeout) {
            connect(
                group = group,
                artifact = artifact,
                version = version,
                file = file,
                method = "HEAD"
            ).connect().useAsync { connection ->
                if (connection.getResponseCode() == 200) {
                    connection.getResponseHeaders().contentLength?.toLong()
                } else {
                    null
                }
            }
        }


    override suspend fun get(group: MavenGroup, artifact: String, version: MavenVersion, file: String): AsyncInput? {
        return withTimeoutOrNull(getTimeout) {
            SafeException.async {
                logger.info("Searching ${group.asString}:$artifact:${version.asString}/file")
                val connection = connect(
                    group = group,
                    artifact = artifact,
                    version = version,
                    file = file,
                    method = "GET"
                ).connect().closeOnException()
                if (connection.getResponseCode() != 200) {
                    logger.info("Artifact ${group.asString}:$artifact:${version.asString}/$file not found")
                    connection.asyncClose()
                    null
                } else {
                    logger.info("Returning ${group.asString}:$artifact:${version.asString}")
                    AutoCloseAsyncInput(
                        delegate = connection.getInput(),
                        closable = {
                            connection.asyncClose()
                        }
                    )
                }
            }
        }
    }

    override suspend fun push(
        group: MavenGroup,
        artifact: String,
        version: MavenVersion,
        file: String,
        from: AsyncInput,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun getMetaData(group: MavenGroup, artifact: String): MavenMetadata? {
        TODO("Not yet implemented")
    }

    override suspend fun getMetaData(group: MavenGroup, artifact: String, version: MavenVersion): MavenMetadata? {
        TODO("Not yet implemented")
    }
}