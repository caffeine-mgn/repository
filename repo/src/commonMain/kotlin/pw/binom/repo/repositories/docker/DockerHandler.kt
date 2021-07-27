package pw.binom.repo.repositories.docker

import pw.binom.copyTo
import pw.binom.crypto.Sha256MessageDigest
import pw.binom.io.ByteArrayOutput
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.relative
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.infoSync
import pw.binom.logger.warnSync
import pw.binom.nextUuid
import pw.binom.repo.*
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.repo.users.UsersService
import pw.binom.toUUIDOrNull
import pw.binom.wrap
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
class DockerHandler(
    urlPrefix: String,
//    val data: DockerDatabase2,
    val path: File,
    val blobs: List<BlobStorageService>,
    val repo: Repo,
    val allowAppend: Boolean,
    val allowRewrite: Boolean,
    val usersService: UsersService,
) : Handler, Repository {

    private val logger = Logger.getLogger("Docker /repositories/$urlPrefix")
    private lateinit var data: DockerIndex

    override suspend fun start() {
        super.start()
        path.mkdirs()
        data = DockerIndex.open(path.relative("index.db"))
    }

    init {
        logger.infoSync("Started")
        if (blobs.isEmpty()) {
            logger.warnSync("No Blobs")
        }
//        if (blobs.isEmpty()) {
//            throw IllegalArgumentException("Blobs is empty")
//        }
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
    private val MANIFEST = "/repositories/$prefix/v2/{name}/manifests/{version}"
    override suspend fun request(req: HttpRequest) {
        if (req.request == INDEX) {
            index(req)
            return
        }
        val u: Any? = when {
            req.method == "GET" && req.request == INDEX -> index(req)
            (req.method == "HEAD" || req.method == "GET") && req.path.isMatch(GET_BLOB) -> getBlob(req)
            (req.method == "HEAD" || req.method == "GET") && req.path.isMatch(MANIFEST) -> getManifest(req)
            req.method == "POST" && req.path.isMatch(PREPARE_UPLOAD) -> prepareBlobUpload(req)
            req.method == "PATCH" && req.path.isMatch(UPLOAD) -> uploadBlob(req)
            req.method == "PUT" && req.path.isMatch(UPLOAD) -> finishUploadBlob(req)
            req.method == "PUT" && req.path.isMatch(MANIFEST) -> uploadManifest(req)
            else -> null
        }
        if (u == null) {
            req.response().use {
                it.status = 400
            }
        }
    }

    private suspend fun prepareBlobUpload(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.WRITE)
        val uuid = Random.nextUuid()
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
        val uuid = req.path.getVariable("digest", UPLOAD) ?: ""
            val blobSize = req.readBinary().use { input ->
                    blobs[0].store(
                        id = uuid.toUUIDOrNull() ?: throw IllegalArgumentException("Invalid UUID"),
                        append = false,
                        input = input,
                    )
            }
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

    private suspend fun getManifest(req: HttpRequest): Boolean {
        val vars = req.path.getVariables(MANIFEST)!!
        val version = vars["version"]!!

        val label = if (version.startsWith("sha256:")) {
            data.getLabelByDigest(version.substring(7).fromHex())
        } else {
            data.getLabelByName(name = vars["name"]!!, version = version)
        }
        if (label == null) {
            req.response {
                it.status = 404
            }
            return true
        }
        req.response {
            val data = label.body.encodeToByteArray()
            it.status = 200
            it.headers["Docker-Content-Digest"] = "sha256:${label.digest.toHex()}"
            it.headers.contentType = label.contentType
            it.headers.contentLength = data.size.toULong()
//            if (req.method.lowercase() == "get") {
            it.startWriteBinary().use { w ->
                data.wrap { data ->
                    w.write(data)
                }
                w.flush()
            }
//            it.sendBinary(data)
//                it.sendText(label.body)
//            }
        }
        return true
    }

    private suspend fun getBlob(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.READ)
        val shaHex = req.path.getVariable("digest", "*/sha256:{digest}") ?: ""
        val blobMeta = data.findByLayoutDigest(shaHex.fromHex())
        if (blobMeta == null) {
            req.response {
                it.headers.keepAlive = false
                it.status = 404
            }
            return true
        }
        val blobStore = blobs.find { it.id == blobMeta.storageId } ?: TODO()
        val size = blobStore.getSize(blobMeta.blobId)
        req.response {
            it.status = 200
            it.headers.keepAlive = false
            it.headers.contentLength = size
            it.contentType(/*blobMeta.mimeType ?:*/ "application/octet-stream")
            if (req.method == "GET") {
                it.sendBinary { output ->
                    blobs[0].getData(blobMeta.id, output)
                }
            }
        }
        return true
    }

    private suspend fun uploadManifest(req: HttpRequest): Boolean {
        val manifest = ByteArrayOutput()
        req.readBinary().use {
            it.copyTo(manifest)
        }

        val sha = Sha256MessageDigest()
        val data = manifest.data
        data.flip()
        data.compact()
        sha.update(data)
        val uuid = Random.nextUuid()
        val digest = sha.finish()
        val prefixLength = "/repositories/$prefix/v2/".length
        val versionPos = req.request.lastIndexOf("/")
        if (versionPos == -1) {
            TODO("Can't find end. Full request is: [${req.request}]")
        }

        val version = req.request.substring(versionPos + 1)
        val name = req.request.substring(startIndex = prefixLength, endIndex = versionPos - "/manifests".length)
        data.clear()
        this.data.upsertLabel(
            name = name,
            version = version,
            data = data.toByteArray().decodeToString(),
            digest = digest,
            contentType = req.headers.contentType ?: ""
        )
        req.response {
            it.headers["Docker-Content-Digest"] = "sha256:${digest.toHex()}"
            it.headers.contentLength = 0uL
            it.status = 201
            it.headers.keepAlive = false
//            it.headers.location = "/v2/blobs/$uuid"
//            it.headers.contentLength = 0uL
//            it.headers[DOCKER_CONTENT_DIGEST] = "sha256:$digestStr"
        }
        return true
    }

    private suspend fun finishUploadBlob(req: HttpRequest): Boolean {
        checkAccess(req, UsersService.RepositoryOperationType.WRITE)
        val digestStr = req.query?.firstOrNull("digest")?.removePrefix("sha256:") ?: return false
        val length = req.headers.contentLength
        val uuid = req.path.getVariable("digest", UPLOAD)?.toUUIDOrNull()
            ?: throw IllegalArgumentException("Invalid UUID")
        val oldBlob = data.findBlobById(uuid)
//        if (oldBlob == null) {
//            req.response {
//                it.status = 404
//            }
//            return true
//        }
        val storeBlob = blobs.find { it.id == uuid }
//        if (storeBlob == null) {
//            logger.warn("Can't find storage with uploaded blob")
//            req.response {
//                it.status = 404
//            }
//            return true
//        }
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
            if (items != null) {
                if (storeBlob?.getSize(uuid) != items[0].toULong()) {
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

                if (data.findByLayoutDigest(digestStr.fromHex()) != null) {
                    checkAccess(req, UsersService.RepositoryOperationType.REWRITE)
                }
                req.readBinary().use { input ->
                    blobs[0].store(id = uuid, append = true, input = input)
                }
                data.insertLayout(
                    id = uuid,
                    blobId = uuid,
                    storageId = blobs[0].id,
                    digest = digestStr.fromHex(),
                )
            }
        }
        if (oldBlob == null) {
            data.insertLayout(
                id = uuid,
                blobId = uuid,
                storageId = blobs[0].id,
                digest = digestStr.fromHex()
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
            req.response {
                it.headers.requestBasicAuth()
                it.headers.keepAlive = false
                it.headers[DOCKER_DISTRIBUTION_API_VERSION] = "registry/2.0"
                it.sendText(generateUnauthorized(msg = msg))
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

    override suspend fun asyncClose() {
        TODO("Not yet implemented")
    }
}