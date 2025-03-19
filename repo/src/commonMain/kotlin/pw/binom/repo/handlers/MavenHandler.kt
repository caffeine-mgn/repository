package pw.binom.repo.handlers

import pw.binom.copyTo
import pw.binom.io.http.Headers
import pw.binom.io.http.headersOf
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.useAsync
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.repo.AbstractHttpHandler
import pw.binom.repo.repositories.maven.MavenGroup
import pw.binom.repo.maven.repositories.MavenRepository
import pw.binom.repo.repositories.maven.MavenResource
import pw.binom.repo.maven.MavenVersion

class MavenHandler(
    val repository: MavenRepository,
) : AbstractHttpHandler() {
    companion object {
        private const val MAVEN_METADATA_XML = "maven-metadata.xml"
        private const val MAVEN_METADATA_XML_MD5 = "maven-metadata.xml.md5"
        private const val MAVEN_METADATA_XML_SHA1 = "maven-metadata.xml.sha1"
        private const val CONTENT_TYPE_XML = "application/xml"
        private const val CONTENT_TYPE_TEXT = "text/plain"
        private const val CONTENT_TYPE_JAR = "application/java-archive"
        private const val CONTENT_TYPE_MODULE = "application/vnd.org.gradle.module+json"
        private const val CONTENT_TYPE_JSON = "application/json"
    }

    private val logger = Logger.getLogger("MavenHandler $repository")

    private fun fileNameToMime(fileName: String) =
        when {
            fileName.endsWith(".xml") -> CONTENT_TYPE_XML
            fileName.endsWith(".txt")
                    || fileName.endsWith(".md5")
                    || fileName.endsWith(".sha1")
                    || fileName.endsWith(".asc") -> CONTENT_TYPE_TEXT

            fileName.endsWith(".jar") -> CONTENT_TYPE_JAR
            fileName.endsWith(".module") -> CONTENT_TYPE_MODULE
            fileName.endsWith(".json") -> CONTENT_TYPE_JSON
            else -> null
        }

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun processing(exchange: HttpServerExchange) {
        val resource = MavenResource.parseURI(exchange.path.raw.removePrefix("/"))
        val artifactName = resource.artifact
        val group = resource.group
        val version = (resource as? MavenResource.InVersion)?.version
        val fileName = resource.file

        val input = if (version == null) {
            repository.getBlob(group = group, artifact = artifactName, file = fileName)
        } else {
            repository.getBlob(group = group, artifact = artifactName, file = fileName, version = version)
        }


        if (fileName == MAVEN_METADATA_XML || fileName == MAVEN_METADATA_XML_MD5 || fileName == MAVEN_METADATA_XML_SHA1) {
            logger.info("$group:$artifactName/$fileName")
            when (fileName) {
                MAVEN_METADATA_XML -> {
                    val metadataText = repository.getMetaDataText(group = group, artifact = artifactName)
                    if (metadataText == null) {
                        exchange.startResponse(404)
                        return
                    }
                    val resp = exchange.response()
                    resp.headers.contentType = CONTENT_TYPE_XML
                    resp.status = 200
                    if (exchange.requestMethod == "GET") {
                        resp.send(metadataText)
                    }
                    return
                }

                MAVEN_METADATA_XML_MD5 -> {
                    val bytes = repository.getMetaDataMd5(group = group, artifact = artifactName)
                    if (bytes == null) {
                        exchange.startResponse(404)
                        return
                    }
                    val resp = exchange.response()
                    resp.status = 200
                    resp.headers.contentType = CONTENT_TYPE_TEXT
                    if (exchange.requestMethod == "GET") {
                        resp.send(bytes.toHexString())
                    }
                    return
                }

                MAVEN_METADATA_XML_SHA1 -> {
                    val bytes = repository.getMetaDataSha1(group = group, artifact = artifactName)
                    if (bytes == null) {
                        exchange.startResponse(404)
                        return
                    }
                    val resp = exchange.response()
                    resp.status = 200
                    resp.headers.contentType = CONTENT_TYPE_TEXT
                    if (exchange.requestMethod == "GET") {
                        resp.send(bytes.toHexString())
                    }
                    return
                }
            }
            exchange.startResponse(404)
            return
        } else {
            val version = MavenVersion(elements.removeLastOrNull() ?: return)
            val artifactName = elements.removeLastOrNull() ?: return
            val group = MavenGroup(elements.joinToString("."))

            if (exchange.requestMethod == "GET") {
                logger.info("Searching $group:$artifactName:$version/$fileName in $repository")
                val stream = repository.get(
                    group = group,
                    artifact = artifactName,
                    version = version,
                    file = fileName,
                )
                if (stream == null) {
                    exchange.startResponse(404)
                    return
                }
                stream.useAsync { stream ->
                    val resp = exchange.response()
                    resp.status = 200
                    resp.headers.contentType = fileNameToMime(fileName)
                    resp.writeBinary {
                        stream.copyTo(it)
                    }
                }
            } else {
                if (exchange.requestMethod == "HEAD") {
                    val isExist = repository.isExist(
                        group = group,
                        artifact = artifactName,
                        version = version,
                        file = fileName,
                    )
                    if (isExist != null) {
                        val contentType = fileNameToMime(fileName)
                        if (contentType != null) {
                            exchange.startResponse(
                                200,
                                headersOf(
                                    Headers.CONTENT_TYPE to contentType,
                                    Headers.CONTENT_LENGTH to isExist.toULong().toString()
                                )
                            )
                        } else {
                            exchange.startResponse(
                                200, headersOf(
                                    Headers.CONTENT_LENGTH to isExist.toULong().toString()
                                )
                            )
                        }
                    } else {
                        exchange.startResponse(404)
                    }
                }
            }
        }
    }
}