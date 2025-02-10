package pw.binom.repo.repositories.docker

import kotlinx.serialization.json.Json
import pw.binom.*
import pw.binom.crypto.Sha256MessageDigest
import pw.binom.flux.*
import pw.binom.io.*
import pw.binom.io.file.*
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.HttpRequest
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.repo.*
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.*
import pw.binom.repo.users.UsersService
import pw.binom.strong.Strong
import kotlin.random.Random

const val DOCKER_CONTENT_DIGEST = "Docker-Content-Digest"
const val DOCKER_UPLOAD_UUID = "Docker-Upload-UUID"
const val DOCKER_DISTRIBUTION_API_VERSION = "Docker-Distribution-API-Version"

//class DockerRepositoryService(
//    val strong: Strong,
//    val urlPrefix: String,
//    val path: File,
//    val allowRewrite: Boolean,
//    val allowAppend: Boolean,
//) : Repository, Strong.InitializingBean, Strong.DestroyableBean, AbstractRoute() {
//
//    private val prefix = if (urlPrefix.isEmpty() || urlPrefix == "/") {
//        ""
//    } else {
//        urlPrefix
//    }
//    private val rootRouter by strong.service<Route>(name = ROOT_ROUTER)
//    private val usersService by strong.service<UsersService>()
//    private val blobStorages by strong.serviceList<BlobStorageService>()
//    private val logger = Logger.getLogger("DockerRepository /${prefix.removePrefix("/")}")
//    private val db = DockerDatabase2(path.relative("index.db"))
//
//    init {
//        if (blobStorages.isEmpty()) {
//            logger.severe("Can't find ant storage")
//            throw IllegalStateException("No any blob storage")
//        }
//        get("$prefix/v2/", this::index)
//        post("$prefix/v2/*/blobs/uploads/", this::prepareBlobUpload)
//        put("$prefix/v2/blobs/*", this::finishUploadBlob)
//        patch("$prefix/v2/blobs/*", this::uploadBlob)
//        head("$prefix/v2/*/blobs/sha256:*", this::getBlob)
//        get("$prefix/v2/*/blobs/sha256:*", this::getBlob)
//        put("$prefix/v2/*/manifests/*", this::putManifests)
//        head("$prefix/v2/*/manifests/*", this::getManifests)
//        get("$prefix/v2/*/manifests/*", this::getManifests)
//    }
//
//    override suspend fun destroy() {
//        rootRouter.detach("$prefix/v2/*", this)
//    }
//
//    override suspend fun init() {
//        rootRouter.route("$prefix/v2/*", this)
//    }
//
//    private fun getImageAndLabel(req: HttpRequest): Pair<String, String>? {
//        val prefix = "$prefix/v2/"
//        if (!req.contextUri.startsWith(prefix)) {
//            return null
//        }
//        val p = req.contextUri.lastIndexOf('/')
//        if (p == -1) {
//            return null
//        }
//        val label = req.contextUri.substring(p + 1)
//
//
//        val imgName = req.contextUri.substring(prefix.length, p - 10)
//        return imgName to label
//    }
//
//
//    private suspend fun checkAccess(action: Action, type: UsersService.RepositoryOperationType) {
//        suspend fun req(msg: String) {
//            action.resp.requestBasicAuth()
//            action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
//            action.resp.complete().bufferedWriter(closeParent = false).use {
//                it.append(
//                    generateUnauthorized(msg = msg)
//                )
//                it.flush()
//            }
//        }
//
//        if (!allowAppend && type == UsersService.RepositoryOperationType.WRITE) {
//            req("access not allowed")
//            throw SecurityRouter.NotAllowedException()
//        }
//
//        if (!allowRewrite && type == UsersService.RepositoryOperationType.REWRITE) {
//            req("access not allowed")
//            throw SecurityRouter.NotAllowedException()
//        }
//
//        val auth = action.req.basicAuth
//        val user = if (auth != null) {
//            val user = usersService.getUser(login = auth.login, password = auth.password)
//            if (user == null) {
//                req("access to write resource is not authorized")
//                throw SecurityRouter.InvalidAuthException()
//            }
//            user
//        } else {
//            null
//        }
//        if (usersService.allowAccess(this, user, type)) {
//            return
//        } else {
//            req("access not allowed")
//            throw SecurityRouter.NotAllowedException()
//        }
//    }
//
//    private suspend fun getManifests(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.READ)
//        val id = getImageAndLabel(action.req) ?: return false
//        if (id.second.startsWith("sha256:")) {
//            val label = db.getLabelByDigest(id.second.removePrefix("sha256:").fromHex())
//            if (label == null) {
//                action.resp.status = 404
//                return true
//            }
//            action.resp.resetHeader(
//                Headers.CONTENT_TYPE,
//                "application/vnd.docker.distribution.manifest.v2+json"
//            )
//            action.resp.resetHeader(Headers.CONTENT_LENGTH, label.size.toULong().toString())
//            action.resp.resetHeader(DOCKER_CONTENT_DIGEST, "sha256:${label.digest.toHex()}")
//            action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
//            action.resp.status = 200
//            if (action.req.method != "HEAD") {
//                action.resp.complete().bufferedAsciiWriter().use {
//                    it.append(label.data)
//                    it.flush()
//                }
//            }
//            return true
//        } else {
//            val label = db.getLabelByName(
//                name = id.first,
//                label = id.second
//            )
//            if (label == null) {
//                action.resp.status = 404
//                return true
//            }
//            action.resp.resetHeader(
//                Headers.CONTENT_TYPE,
//                "application/vnd.docker.distribution.manifest.v2+json"
//            )
//
//            action.resp.resetHeader(Headers.CONTENT_LENGTH, label.size.toULong().toString())
//            action.resp.resetHeader(DOCKER_CONTENT_DIGEST, "sha256:${label.digest.toHex()}")
//            action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
//            action.resp.status = 200
//            if (action.req.method != "HEAD") {
//                action.resp.complete().bufferedAsciiWriter().use {
//                    it.append(label.data)
//                    it.flush()
//                }
//            }
//            return true
//        }
//    }
//
//    private suspend fun putManifests(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.WRITE)
//        val prefix = "$prefix/v2/"
//        if (!action.req.contextUri.startsWith(prefix)) {
//            return false
//        }
//        val p = action.req.contextUri.lastIndexOf('/')
//        if (p == -1) {
//            return false
//        }
//        val label = action.req.contextUri.substring(p + 1)
//        val imgName = action.req.contextUri.substring(prefix.length, p - 10)
//        val txt = action.req.input.bufferedAsciiReader().use {
//            it.readText()
//        }
//        if (db.isLabelExist(name = imgName, label = label)) {
//            checkAccess(action, UsersService.RepositoryOperationType.REWRITE)
//        }
//        val manifestV2 = Json.decodeFromString(DockerManifest2Single.serializer(), txt)
//        blobStorages[0].updateContentType(
//            manifestV2.config.digest.removePrefix("sha256:").fromHex(),
//            manifestV2.config.mediaType
//        )
//        manifestV2.layers.forEach {
//            blobStorages[0].updateContentType(it.digest.removePrefix("sha256:").fromHex(), it.mediaType)
//        }
//        action.resp.status = 201
//        action.resp.resetHeader(DOCKER_CONTENT_DIGEST, "sha256:" + txt.calcSha256().toHex())
//        action.resp.resetHeader(Headers.LOCATION, "/v2/$imgName/manifests/$label")
//        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
//        action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
//        db.upsertLabel(name = imgName, label = label, data = txt)
//        db.insertLayout(
//            name = imgName,
//            label = label,
//            layouts = manifestV2.layers.map {
//                it.digest.removePrefix("sha256:").fromHex()
//            } + listOf(manifestV2.config.digest.removePrefix("sha256:").fromHex()))
//        return true
//    }
//
//    private suspend fun index(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.READ)
//        action.resp.resetHeader(DOCKER_DISTRIBUTION_API_VERSION, "registry/2.0")
//        action.resp.status = 200
//        return true
//    }
//
//    private suspend fun prepareBlobUpload(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.WRITE)
//        val uuid = Random.uuid()
//        println("POST ${action.req.getAsCurl()}")
//        action.resp.enableCompress = false
//        action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
//        action.resp.resetHeader(Headers.RANGE, "0-0")
//        action.resp.resetHeader(DOCKER_UPLOAD_UUID, uuid.toString())
//        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
//        action.resp.status = 202
//        return true
//    }
//
//    private suspend fun getBlob(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.READ)
//        val p = action.req.contextUri.lastIndexOf("/sha256:")
//        if (p == -1) {
//            return false
//        }
//        val shaHex = action.req.contextUri.substring(p + 8)
//        val blobMeta = blobStorages[0].getBlobByDigest(shaHex.fromHex())
//        if (blobMeta == null) {
//            action.resp.status = 404
//            return true
//        }
//
//        action.resp.resetHeader(Headers.CONTENT_LENGTH, blobMeta.size.toULong().toString())
//        action.resp.resetHeader(Headers.CONTENT_TYPE, blobMeta.contentType)
//        action.resp.status = 200
//        if (action.req.method != "HEAD") {
//            blobStorages[0].getDataByDigest(shaHex.fromHex(), action.resp.complete())
//        }
//        return true
//    }
//
//    private suspend fun uploadBlob(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.WRITE)
//        println("Patch Blob: ${action.req.getAsCurl()}")
//        val p = action.req.contextUri.lastIndexOf('/')
//        if (p == -1) {
//            return false
//        }
//        action.resp.status = 202
//        val uuid = action.req.contextUri.substring(p + 1)
//        val blob = blobStorages[0].storeById(
//            id = UUID.fromString(uuid),
//            contentType = "unknown",
//            append = false,
//            input = action.req.input
//        )
//
//        action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
//        action.resp.resetHeader(Headers.RANGE, "0-${blob.size.toULong()}")
//        action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
//        action.resp.resetHeader(DOCKER_UPLOAD_UUID, uuid)
//        return true
//    }
//
//    private suspend fun finishUploadBlob(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.WRITE)
//        try {
//            action.resp.enableCompress = false
//            val digestStr =
//                action.req.parseGetParams()["digest"]?.singleOrNull()?.removePrefix("sha256:") ?: return false
//            val length = action.req.headers[Headers.CONTENT_LENGTH]?.singleOrNull()
//            val s = action.req.contextUri.lastIndexOf('/')
//            val p = action.req.contextUri.lastIndexOf('?')
//            if (s == -1 || p == -1) {
//                logger.info("Invalid URI")
//                return false
//            }
//            val uuid = action.req.contextUri.substring(s + 1, p)
//            val oldBlob = blobStorages[0].getBlobById(UUID.fromString(uuid))
//            if (oldBlob == null) {
//                action.resp.status = 404
//                return true
//            }
//            if (length != null && length != "0") {
//                val items =
//                    action.req.headers[Headers.CONTENT_RANGE]
//                        ?.singleOrNull()
//                        ?.removePrefix("bytes=")
//                        ?.split('-', limit = 2)
//                if (items != null) {
//
//                    if (oldBlob.size != items[0].toULong().toLong()) {
//                        action.resp.status = 416
//                        return true
//                    }
//                    if (items[0].toLong() > items[1].toLong()) {
//                        action.resp.status = 416
//                        return true
//                    }
//                }
//            }
//            println("Write input to file...")
//            if (blobStorages[0].isExist(digestStr.fromHex())) {
//                checkAccess(action, UsersService.RepositoryOperationType.REWRITE)
//            }
//            blobStorages[0].storeById(UUID.fromString(uuid), "unknown", true, action.req.input)
//            action.resp.resetHeader(Headers.LOCATION, "/v2/blobs/$uuid")
//            action.resp.resetHeader(Headers.CONTENT_LENGTH, "0")
//            action.resp.resetHeader(DOCKER_CONTENT_DIGEST, digestStr)
//            action.resp.status = 201
//            return true
//        } catch (e: Throwable) {
//            e.printStackTrace()
//            throw e
//        }
//    }
//}

fun File.calcSha256(bufferSize:Int): ByteArray {
    val sha = Sha256MessageDigest()
    ByteBuffer(bufferSize).use { buf ->
        openRead().use {
            while (true) {
                buf.clear()
                val l = it.read(buf)
                if (l.isNotAvailable) {
                    break
                }
                buf.flip()
                sha.update(buf)
            }
        }
    }
    return sha.finish()
}

fun String.calcSha256(): ByteArray {
    val s = Sha256MessageDigest()
    encodeToByteArray().wrap().use { buf ->
        s.update(buf)
    }
    return s.finish()
}