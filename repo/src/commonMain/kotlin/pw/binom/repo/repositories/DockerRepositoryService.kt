package pw.binom.repo.repositories

import kotlinx.serialization.json.Json
import pw.binom.*
import pw.binom.flux.Action
import pw.binom.flux.Route
import pw.binom.flux.get
import pw.binom.flux.post
import pw.binom.io.bufferedAsciiInputReader
import pw.binom.io.bufferedWriter
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.relative
import pw.binom.io.file.write
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.basicAuth
import pw.binom.io.httpServer.requestBasicAuth
import pw.binom.io.readText
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.repo.ConfigService
import pw.binom.repo.generateUnauthorized
import pw.binom.repo.getAsCurl
import pw.binom.repo.parseGetParams
import pw.binom.strong.Strong
import kotlin.random.Random

private const val DOCKER_CONTENT_DIGEST = "Docker-Content-Digest"
private const val DOCKER_UPLOAD_UUID = "Docker-Upload-UUID"
private const val DOCKER_DISTRIBUTION_API_VERSION = "Docker-Distribution-API-Version"

class DockerRepositoryService(
    val strong: Strong,
    val urlPrefix: String,
    val path: File,
) : Repository, Strong.InitializingBean {
    private val blobsDir = path.relative("blobs")
    private val rootRouter by strong.service<Route>()
    private val configService by strong.service<ConfigService>()

    val logger = run {
        val loggerPrefix = if (urlPrefix.isEmpty() || urlPrefix == "/") {
            "/"
        } else {
            urlPrefix
        }
        Logger.getLogger("DockerRepository $loggerPrefix")
    }

    private val prefix = if (urlPrefix.isEmpty() || urlPrefix == "/") {
        ""
    } else {
        urlPrefix
    }
    val pool = ByteBufferPool(10)
    override suspend fun init() {
        path.mkdirs()
        blobsDir.mkdirs()
        rootRouter.endpoint("HEAD", "$prefix/v2/*/blobs/sha256:*", this::headBlob)
        rootRouter.endpoint("PATCH", "$prefix/v2/blobs/*", this::patchBlob)
        rootRouter.endpoint("PUT", "$prefix/v2/blobs/*", this::putBlob)
        rootRouter.endpoint("PUT", "$prefix/v2/*/manifests/*", this::putManifests)
        rootRouter.get("$prefix/v2/*/blobs/sha256:*", this::getBlob)

        rootRouter.get("$prefix/v2/", this::index)

        rootRouter.post("$prefix/v2/*/blobs/uploads/", this::postBlob)
        logger.info("Started")
        logger.info("Repository Directory: $path")
    }

    private fun headBlob(action: Action): Boolean {
        val p = action.req.contextUri.lastIndexOf("/sha256:")
        if (p == -1) {
            return false
        }
        val shaHex = action.req.contextUri.substring(p + 8)
        val img = blobsDir.relative(shaHex)
        if (img.isFile) {
            action.resp.resetHeader(Headers.CONTENT_LENGTH, img.size.toULong().toString())
            action.resp.status = 200
            return true
        }
        action.resp.status = 404
        return true
    }

    private suspend fun putBlob(action: Action): Boolean {
        action.resp.enableCompress = false
        val digest = action.req.parseGetParams()["digest"]?.singleOrNull()?.removePrefix("sha256:") ?: return false
        val length = action.req.headers[Headers.CONTENT_LENGTH]?.singleOrNull()
        val s = action.req.contextUri.lastIndexOf('/')
        val p = action.req.contextUri.lastIndexOf('?')
        if (s == -1 || p == -1) {
            logger.info("Invalid URI")
            return false
        }
        val uuid = action.req.contextUri.substring(s + 1, p)
        val file = blobsDir.relative(uuid)
        if (!file.isFile) {
            logger.info("Can't find file [$uuid]")
            return false
        }
        if (length != null && length != "0") {
            val items =
                action.req.headers[Headers.CONTENT_RANGE]
                    ?.singleOrNull()
                    ?.removePrefix("bytes=")
                    ?.split('-', limit = 2)
            if (items != null) {
                if (file.size != items[0].toULong().toLong()) {
                    action.resp.status = 416
                    return true
                }
                if (items[0].toLong() > items[1].toLong()) {
                    action.resp.status = 416
                    return true
                }
            }
        }
        file.write(append = true).use {
            action.req.input.copyTo(it)
        }
        action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
        action.resp.resetHeader(DOCKER_CONTENT_DIGEST, digest)
        file.renameTo(blobsDir.relative(digest))
        action.resp.status = 201
        return true
    }


    private suspend fun patchBlob(action: Action): Boolean {
        val p = action.req.contextUri.lastIndexOf('/')
        if (p == -1) {
            return false
        }
        action.resp.status = 202
        val uuid = action.req.contextUri.substring(p + 1)
        val tmpFile = blobsDir.relative(uuid)
        tmpFile.write().use {
            action.req.input.copyTo(it, DEFAULT_BUFFER_SIZE * 5)
            it.flush()
        }

        action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
        action.resp.resetHeader(Headers.RANGE, "0-${tmpFile.size.toULong()}")
        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
        action.resp.resetHeader(DOCKER_UPLOAD_UUID, uuid)
        return true
    }

    private suspend fun postBlob(action: Action): Boolean {
        val data = action.req.input.copyTo(NullAsyncOutput)
        val uuid = Random.uuid()
        action.resp.enableCompress = false
        action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
        action.resp.resetHeader(Headers.RANGE, "0-0")
        action.resp.resetHeader(DOCKER_UPLOAD_UUID, uuid.toString())
        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
        action.resp.status = 202

        return true
    }

    private suspend fun index(action: Action): Boolean {
        val u = action.req.basicAuth
        if (u == null) {
            action.resp.requestBasicAuth()
            action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
            action.resp.complete().bufferedWriter(closeParent = false).use {
                it.append(
                    generateUnauthorized(msg = "access to write resource is not authorized")
                )
                it.flush()
            }
            return true
        }
        action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
        action.resp.status = 200
        return true
    }

    private suspend fun putManifests(action: Action): Boolean {
        println("put ${action.req.getAsCurl()}")
        val txt = action.req.input.bufferedAsciiInputReader().readText()
        try {
            val manifest = Json.decodeFromString(DockerManifest.serializer(), txt)
        } catch (e: Throwable) {
            logger.warn("Can't parse manifest.")
        }
        println(txt)
        return false
    }

    private fun getBlob(action: Action): Boolean {
        return false
    }
}