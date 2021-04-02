package pw.binom.repo.repositories.maven

import pw.binom.flux.*
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.relative
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.repo.ROOT_ROUTER
import pw.binom.repo.SecurityRouter
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.repo.users.UsersService
import pw.binom.strong.Strong
import pw.binom.uuid
import kotlin.random.Random

class MavenRepositoryService(
    strong: Strong,
    val urlPrefix: String,
    val allowRewrite: Boolean,
    val allowAppend: Boolean,
    val path: File,
) : Repository, Strong.InitializingBean, Strong.DestroyableBean, Handler {
    override suspend fun destroy(strong: Strong) {
        TODO("Not yet implemented")
    }

    override suspend fun init(strong: Strong) {
        TODO("Not yet implemented")
    }

    override suspend fun request(req: HttpRequest) {
        TODO("Not yet implemented")
    }
//    init {
//        path.mkdirs()
//    }
//
//    private val prefix = if (urlPrefix.isEmpty() || urlPrefix == "/") {
//        ""
//    } else {
//        urlPrefix
//    }
//    private val logger = Logger.getLogger("MavenRepository /${prefix.removePrefix("/")}")
//    private val rootRouter by strong.service<Route>(name = ROOT_ROUTER)
//    private val usersService by strong.service<UsersService>()
//    private val blobStorages by strong.serviceList<BlobStorageService>()
//    private val index = MavenIndexer(path.relative("index.db"))
//
//
//    init {
//        logger.info("init maven repo [$prefix/*]")
//        get("$prefix/*", this::get)
//        head("$prefix/*", this::get)
//        post("$prefix/*", this::post)
//
//    }
//
//    override suspend fun destroy() {
//        rootRouter.detach("$prefix/*", this)
//    }
//
//    override suspend fun init() {
//        rootRouter.route("$prefix/*", this)
//    }
//
//    private suspend fun checkAccess(action: Action, type: UsersService.RepositoryOperationType) {
//        suspend fun req(msg: String) {
//            action.resp.requestBasicAuth()
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
//    private suspend fun get(action: Action): Boolean {
//        checkAccess(action, UsersService.RepositoryOperationType.READ)
//        val maven = action.req.getMavenFile()
//        val id = index.get(
//            group = maven.group,
//            artifact = maven.artifact,
//            name = maven.name,
//            version = maven.version
//        )
//
//        if (id == null) {
//            action.resp.status = 404
//            return true
//        }
//        val blob = blobStorages[0].getBlobById(id)
//        if (blob == null) {
//            index.removeByBlobId(id)
//            action.resp.status = 404
//            return true
//        }
//
//        action.resp.status = 200
//        action.resp.resetHeader(Headers.CONTENT_LENGTH, blob.size.toULong().toString())
//        if (action.req.method != "HEAD") {
//            blobStorages[0].getDataById(id, action.resp.complete())
//        }
//        return true
//    }
//
//    private suspend fun post(action: Action): Boolean {
//        if (!allowAppend) {
//            action.resp.status = 403
//            return true
//        }
//        checkAccess(action, UsersService.RepositoryOperationType.WRITE)
//        val maven = action.req.getMavenFile()
//        val contentType = action.req.headers[Headers.CONTENT_TYPE]?.firstOrNull() ?: "application/x-www-form-urlencoded"
//        var id = index.get(group = maven.group, artifact = maven.artifact, name = maven.name, version = maven.version)
//
//        if (id != null) {
//            checkAccess(action, UsersService.RepositoryOperationType.REWRITE)
//        }
//
//        id = id ?: Random.uuid()
//        blobStorages[0].storeById(id = id, contentType = contentType, append = false, input = action.req.input)
//        index.rewrite(
//            group = maven.group,
//            artifact = maven.artifact,
//            version = maven.version,
//            name = maven.name,
//            contentType = contentType,
//            blobId = id,
//        )
//        action.resp.status = 202
//
//        return true
//    }
//
//    private fun HttpRequest.getMavenFile(): FileRef {
//        val fileNameIndex = contextUri.lastIndexOf('/')
//        val fileName = contextUri.substring(fileNameIndex + 1)
//        val version: String?
//        val artifact: String
//        val group: String
//        if (fileName == "maven-metadata-local.xml") {
//            val artifactIndex = contextUri.lastIndexOf('/', fileNameIndex - 1)
//            artifact = contextUri.substring(artifactIndex + 1, fileNameIndex)
//            group = contextUri.substring(prefix.length + 1, artifactIndex).replace('/', '.')
//            version = null
//        } else {
//            val versionIndex = contextUri.lastIndexOf('/', fileNameIndex - 1)
//            val artifactIndex = contextUri.lastIndexOf('/', versionIndex - 1)
//            group = contextUri.substring(prefix.length + 1, artifactIndex).replace('/', '.')
//            artifact = contextUri.substring(artifactIndex + 1, versionIndex)
//            version = contextUri.substring(versionIndex + 1, fileNameIndex)
//
//        }
//        return FileRef(
//            group = group,
//            artifact = artifact,
//            version = version,
//            name = fileName
//        )
//    }
}

private class FileRef(val group: String, val artifact: String, val version: String?, val name: String)
