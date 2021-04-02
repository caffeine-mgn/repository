package pw.binom.repo.repositories.docker

import pw.binom.date.Date
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.use
import pw.binom.repo.*
import pw.binom.repo.blob.BlobIndex
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.repo.users.UsersService
import pw.binom.toUUIDOrNull
import pw.binom.uuid
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
class DockerHandler(
    urlPrefix: String,
    val data: DockerDatabase2,
    val blobs: List<BlobStorageService>,
    val repo: Repo,
    val allowAppend: Boolean,
    val allowRewrite: Boolean,
    val usersService: UsersService,
) : Handler, Repository {

    init {
        if (blobs.isEmpty()) {
            throw IllegalArgumentException("Blobs is empty")
        }
    }

    private val prefix = if (urlPrefix.isEmpty() || urlPrefix == "/") {
        ""
    } else {
        urlPrefix
    }

    private val INDEX = "/repositories/$prefix/v2/"
    private val PREPARE_UPLOAD = "/repositories/$prefix/v2/{image-name}/blobs/uploads/"
    private val UPLOAD = "/repositories/$prefix/v2/blobs/{digest}"
    private val GET_BLOB = "/repositories/$prefix/v2/*/blobs/sha256:*"
    private val MANIFEST = "/repositories/$prefix/v2/*/manifests/*"

    override suspend fun request(req: HttpRequest) {
        if (req.request == INDEX) {
            index(req)
            return
        }
        val u: Any? = when {
            req.method == "GET" && req.request == INDEX -> index(req)
            (req.method == "HEAD" || req.method == "GET") && req.path.isMatch(GET_BLOB) -> getBlob(req)
            req.method == "POST" && req.path.isMatch(PREPARE_UPLOAD) -> prepareBlobUpload(req)
            req.method == "PATCH" && req.path.isMatch(UPLOAD) -> uploadBlob(req)
            req.method == "PUT" && req.path.isMatch(UPLOAD) -> finishUploadBlob(req)
            else -> null
        }
        if (u == null) {
            println("->${req.method} ${req.request}    prefix=\"$prefix\"")
            req.response().use {
                it.status = 400
            }
        }
    }

    private suspend fun prepareBlobUpload(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.WRITE)
        val uuid = Random.uuid()
        println("POST ${req.getAsCurl()}")
        req.response {
            it.status = 202
            it.headers.keepAlive = false
            it.headers.location = "/v2/blobs/$uuid"
            it.headers[Headers.RANGE] = "0-0"
            it.headers[DOCKER_UPLOAD_UUID] = uuid.toString()
            it.headers.contentLength = 0uL
        }
        return true
    }

    private suspend fun index(req: HttpRequest) {
        checkAccess(req, UsersService.RepositoryOperationType.READ)
        req.response().use {
            it.status = 200
            it.headers.keepAlive = false
            it.headers[DOCKER_DISTRIBUTION_API_VERSION] = "registry/2.0"
        }
    }

    private suspend fun uploadBlob(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.WRITE)
        println("Patch Blob: ${req.getAsCurl()}")
        val uuid = req.path.getVariable("digest", UPLOAD) ?: ""
        val blobStore = blobs[0]
        val blobSize = req.readBinary().use { input ->
            blobs[0].store(
                id = uuid.toUUIDOrNull() ?: throw IllegalArgumentException("Invalid UUID"),
                append = false,
                input = input,
            )
        }
        println("blobSize: $blobSize")
        req.response {
            it.status = 202
            it.headers.keepAlive = false
            it.headers.location = "/v2/blobs/$uuid"
            it.headers[Headers.RANGE] = "0-${blobSize.toULong()}"
            it.headers.contentLength = 0uL
            it.headers[DOCKER_UPLOAD_UUID] = uuid
        }
        return true
    }

    private suspend fun getBlob(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.READ)
        val shaHex = req.path.getVariable("digest", "*/sha256:{digest}") ?: ""
        val blobMeta = blobs[0].index.findByDigest(shaHex.fromHex())
        if (blobMeta == null) {
            req.response {
                it.headers.keepAlive = false
                it.status = 404
            }
            return true
        }
        req.response {
            it.status = 200
            it.headers.keepAlive = false
            it.headers.contentLength = blobMeta.size.toULong()
            it.setContentType(blobMeta.mimeType ?: "application/octet-stream")
            if (req.method == "GET") {
                it.writeBinary().use { output ->
                    blobs[0].getData(blobMeta.id, output)
                }
            }
        }
        return true
    }

    private suspend fun finishUploadBlob(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.WRITE)
        val digestStr = req.query?.firstOrNull("digest")?.removePrefix("sha256:") ?: return false
        val length = req.headers.contentLength
        val uuid = req.path.getVariable("digest", UPLOAD)?.toUUIDOrNull()
            ?: throw IllegalArgumentException("Invalid UUID")
        val oldBlob = blobs[0].index.findById(uuid)
//            if (oldBlob == null) {
//                action.resp.status = 404
//                return true
//            }
        if (length != null && length != 0uL) {
            val items =
                req.headers[Headers.CONTENT_RANGE]
                    ?.singleOrNull()
                    ?.removePrefix("bytes=")
                    ?.split('-', limit = 2)
            if (items != null && oldBlob != null) {

                if (oldBlob.size != items[0].toULong().toLong()) {
                    req.response {
                        it.status = 416
                        it.headers.keepAlive = false
                    }
                    return true
                }
                if (items[0].toLong() > items[1].toLong()) {
                    req.response {
                        it.status = 416
                        it.headers.keepAlive = false
                    }
                    return true
                }

                println("Write input to file...")
                if (blobs[0].index.findByDigest(digestStr.fromHex()) != null) {
                    checkAccess(req, UsersService.RepositoryOperationType.REWRITE)
                }
                req.readBinary().use { input ->
                    blobs[0].store(id = uuid, append = true, input = input)
                }
            }
        }
        if (oldBlob == null) {
            blobs[0].index.insert(
                BlobIndex.Blob(
                    id = uuid,
                    digest = digestStr.fromHex(),
                    size = blobs[0].getSize(uuid)?.toLong() ?: 0L,
                    mimeType = null,
                    uploadDate = Date(),
                    lastUsageDate = Date(),
                )
            )
        }
        req.response {
            it.status = 201
            it.headers.keepAlive = false
            it.headers.location = "/v2/blobs/$uuid"
            it.headers.contentLength = 0uL
            it.headers[DOCKER_CONTENT_DIGEST] = "sha256:$digestStr"
        }
        return true

    }

    private suspend fun checkAccess(req: HttpRequest, type: UsersService.RepositoryOperationType) {
        suspend fun req(msg: String) {
            req.response().apply {
                headers.requestBasicAuth()
                headers.keepAlive = false
                headers[DOCKER_DISTRIBUTION_API_VERSION] = "registry/2.0"
                writeText(generateUnauthorized(msg = msg))
            }
        }

        if (!allowAppend && type == UsersService.RepositoryOperationType.WRITE) {
            req("access not allowed")
            throw SecurityRouter.NotAllowedException()
        }

        if (!allowRewrite && type == UsersService.RepositoryOperationType.REWRITE) {
            req("access not allowed")
            throw SecurityRouter.NotAllowedException()
        }

        val auth = req.headers.basicAuth
        val user = if (auth != null) {
            val user = usersService.getUser(login = auth.login, password = auth.password)
            if (user == null) {
                req("access to write resource is not authorized")
                throw SecurityRouter.InvalidAuthException()
            }
            user
        } else {
            null
        }
        if (usersService.allowAccess(this, user, type)) {
            return
        } else {
            req("access not allowed")
            throw SecurityRouter.NotAllowedException()
        }
    }
}