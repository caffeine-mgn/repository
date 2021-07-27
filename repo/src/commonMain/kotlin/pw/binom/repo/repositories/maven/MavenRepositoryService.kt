package pw.binom.repo.repositories.maven

import pw.binom.UUID
import pw.binom.io.file.File
import pw.binom.io.file.mkdirs
import pw.binom.io.file.relative
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.net.toPath
import pw.binom.nextUuid
import pw.binom.repo.blob.BlobStorageService
import pw.binom.repo.repositories.Repository
import pw.binom.strong.Strong
import kotlin.random.Random

private const val FULL_PATH = "/repositories/*/{name}"

class MavenPtr(val group: String, val artifact: String, val version: String?, val name: String) {
    companion object {
        fun parse(path: String): MavenPtr {
            val p = path.toPath
            val items = p.raw.split('/')
            return if (p.isMatch("*/maven-metadata.xml.*") || p.isMatch("*/maven-metadata.xml")) {
                val name = items[items.lastIndex]
                val artifact = items[items.lastIndex - 1]
                val group = items.subList(0, items.lastIndex - 1).joinToString(".")
                MavenPtr(
                    group = group,
                    artifact = artifact,
                    version = null,
                    name = name,
                )
            } else {
                val name = items[items.lastIndex]
                val version = items[items.lastIndex - 1]
                val artifact = items[items.lastIndex - 2]
                val group = items.subList(0, items.lastIndex - 2).joinToString(".")
                MavenPtr(
                    group = group,
                    artifact = artifact,
                    version = version,
                    name = name,
                )
            }
        }
    }

    override fun toString(): String =
        "$group:$artifact:${version ?: "<no version>"}/$name"
}

class MavenRepositoryService(
    strong: Strong,
    val repositoryName: String,
    val urlPrefix: String,
    val allowRewrite: Boolean,
    val allowAppend: Boolean,
    val path: File,
    val blobs: Map<UUID, BlobStorageService>
) : Repository, Handler {

    private val logger = Logger.getLogger("Maven /repositories/$repositoryName")

    private lateinit var mavenIndexer2: MavenIndexer2

    override suspend fun start() {
        logger.info("Starting")
        path.mkdirs()
        mavenIndexer2 = MavenIndexer2.create(
            repositoryName = repositoryName,
            path.relative("index.db")
        )
    }

    override suspend fun asyncClose() {
        mavenIndexer2.asyncClose()
    }

    override suspend fun request(req: HttpRequest) {


        println("urlPrefix=[$urlPrefix] req.path=[${req.path}]")
        val path = req.path.getVariable("name", FULL_PATH)
        if (path == null) {
            req.response {
                it.status = 404
            }
            return
        }
        val ptr = MavenPtr.parse(path)
        if (req.method == "GET") {
            get(
                req = req,
                ptr = ptr,
            )
            return
        }
        if (req.method == "PUT") {
            put(
                req = req,
                ptr = ptr,
            )
            return
        }
        req.response {
            it.status = 404
            println("Not Found: $ptr ${req.method}")
        }
    }

    private suspend fun put(req: HttpRequest, ptr: MavenPtr) {
        println("Searching...")
        val oldBlobId = mavenIndexer2.find(ptr)
        println("found! oldBlobId=$oldBlobId")
        if (oldBlobId != null) {
            println("deleting...")
            mavenIndexer2.delete(ptr)
            println("deleted!")
        }
        val length = req.headers.contentLength ?: 0uL
        val blob = selectBlob(length.toLong())
        if (blob == null) {
            logger.severe("Can't find blob for storage file $ptr, length: $length bytes")
            req.response {
                it.status = 500
            }
            return
        }
        val blobId = Random.nextUuid()
        req.readBinary().use { input ->
            blob.store(
                id = blobId,
                append = false,
                input = input
            )
        }
        println("Try insert new...")
        mavenIndexer2.insert(
            ptr = ptr,
            blob = blobId,
            storage = blob.id,
        )
        println("Push $ptr. headers: ${req.headers}")
        req.response {
            it.status = 202
        }
    }

    private fun selectBlob(length: Long) =
        blobs.values.asSequence().filter { it.remaining > length }.maxByOrNull { it.remaining }

    private suspend fun get(req: HttpRequest, ptr: MavenPtr) {
        req.response {
            val blobId = mavenIndexer2.find(
                ptr = ptr
            )
            if (blobId == null) {
                logger.info("Can't find file $ptr")
                it.status = 404
                return@response
            }
            val blob = blobs[blobId.storage]
            if (blob == null) {
                it.status = 404
                return@response
            }
            it.status = 200
            logger.info("Founded!")
            it.sendBinary { out ->
                blob.getData(blobId.id, out)
            }
        }
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
