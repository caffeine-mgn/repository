package pw.binom.repo

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pw.binom.*
import pw.binom.flux.RootRouter
import pw.binom.flux.postHandle
import pw.binom.flux.wrap
import pw.binom.io.bufferedAsciiInputReader
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.io.file.read
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.AsyncHttpClient
import pw.binom.io.httpClient.use
import pw.binom.io.httpServer.*
import pw.binom.io.readText
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import pw.binom.network.SocketClosedException
import pw.binom.process.Signal
import pw.binom.strong.Strong

val LOG = Logger.getLogger("Main")

class HttpHandler(private val config: Config, copyBuffer: ByteBufferPool) : Handler {
    //RepoFileSystemAccess(config)
    val fs = LocalFileSystem(config.root, copyBuffer)
    val dav = WebDavHandler(
        prefix = "${config.prefix}/dav",
        fs = fs,
        bufferPool = copyBuffer
    )
    val files = FileSystemHandler(
        title = config.title,
        fs = fs,
        copyBuffer = copyBuffer
    )
    val docker = DockerHandler(config, copyBuffer)

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        println("${req.method} ${urlDecode(req.uri)}")
        try {
            if (req.uri == "/v2" || req.uri.startsWith("/v2/")) {
                docker.request(
                    req.withContextURI(req.uri.removePrefix("/v2")),
                    resp
                )
                return
            }


            if (config.webdavEnable && (req.uri == "/dav" || req.uri.startsWith("/dav/"))) {
                dav.request(
                    req.withContextURI(req.uri.removePrefix("/dav")),
                    resp
                )
                return
            }

            val sb = StringBuilder("curl -X ${req.method} https://images.binom.pw${req.uri} -H 'Host:images.binom.pw'")
            req.headers.forEach { item ->
                when (item.key) {
                    "X-Forwarded-Proto", "Host", "Accept-Encoding", "X-Forwarded-For" -> return@forEach
                }
                item.value.forEach {
                    sb.append(" -H '${item.key}: $it'")
                }
            }
            println(sb)

            files.request(req, resp)
        } catch (e: Throwable) {
            e.printStackTrace()
            throw e
        }
    }
}

class User(val login: String, val password: String, val readOnly: Boolean)

class Config(
        val root: File,
        val allowRewriting: Boolean,
        val allowGuest: Boolean,
        val users: List<User>,
        val prefix: String,
        val webdavEnable: Boolean,
        val title: String
)

private fun printHelp() {
    val simplePathToFile = when (Environment.platform) {
        Platform.MINGW_X86,
        Platform.MINGW_X64 -> "D:\\repository"

        Platform.MACOS,
        Platform.LINUX_64,
        Platform.LINUX_ARM_64,
        Platform.LINUX_ARM_32 -> "/var/repository"

        Platform.JVM,
        Platform.JS -> ""
        else->TODO()
    }

    println("Commands:")
    println("-root=$simplePathToFile    Root directory for repository")
    println("-allowRewriting=true    Allow or Disallow file rewriting")
    println("-allowAnonymous=true    Allow or Disallow Anonymous read access")
    println("-bind=0.0.0.0:8080    Bind address for web server")
    println("-admin=admin:admin123    Define new Administrator. Can upload change repository. ")
    println("-guest=admin:admin123    Define new Guest. Can only read repository. ")
    println("-prefix=/release    Set URI prefix")
    println("-zlib=false    Enable Zlib Encode")
    println("-webdav=true    Enable Web Dav Access")
    println("-title=\"Binom Repository Server\" Title on File List Page")
    println("-h    Shows this help")
}

fun testUpload(server: String) {
    val nd = NetworkDispatcher()

    val client = AsyncHttpClient(nd)
    val auth = BasicAuth("ci", "11")
    val jj = nd.async {
        val url = URL("https://$server.binom.pw:443/v2/test2/test/blobs/uploads/")
//        val url = URL("http://127.0.0.1:7001/v2/test2/test/blobs/uploads/")
        val r = client.request("POST", url)
        r.use(auth)
        val resp = r.response()
        resp.headers.forEach { e ->
            e.value.forEach {
                println("${e.key}: $it")
            }
        }
        println("Code: ${resp.responseCode}")
        val location = resp.headers[Headers.LOCATION]!!.first()
        val patchUrl = url.newURI(location)
        println("patchUrl: [$patchUrl]")
        println("\n\n\n")
        val r2 = client.request("PATCH", patchUrl)
        r2.use(auth)
        val s =
            File("C:\\TEMP\\8\\test_img").read()
                .use {
                    val s = r2.upload()
                    it.copyTo(s)
                    s
                }.response()
        println("Response Headers:")
        s.headers.forEach { e ->
            e.value.forEach {
                println("${e.key}: $it")
            }
        }

        println("Code: ${resp.responseCode}")
        println("Error: ${resp.bufferedAsciiInputReader().readText()}")
    }

    while (!Signal.isInterrupted) {
        if (jj.isDone) {
            if (!!jj.isSuccess) {
                println("All is OK!")
                break
            } else {
                jj.exceptionOrNull!!.printStackTrace()
            }
            return
        }
        nd.select(1000)
    }
}

@Polymorphic
@Serializable
sealed class RepositoryConfig {
    @Polymorphic
    @Serializable
    @SerialName("docker")
    data class Docker(
        val allowRewrite: Boolean,
        val allowAppend: Boolean,
        val path: String,
        val urlPrefix: String
    ) : RepositoryConfig()

    @Polymorphic
    @Serializable
    @SerialName("maven")
    data class Maven(
        val allowRewrite: Boolean,
        val path: String,
        val urlPrefix: String
    ) : RepositoryConfig()
}

@Polymorphic
@Serializable
sealed class UserManagementConfig {
    @Polymorphic
    @Serializable
    @SerialName("embedded")
    data class Embedded(val users: List<User>) : UserManagementConfig() {
        @Serializable
        data class User(val login: String, val password: String)
    }

    data class LDAP(
        val login: String,
        val password: String,
        val hostAddr: String,
        val hostPort: Int,
        val searchDn: String,
    ) : UserManagementConfig()
}

@Serializable
class BindConfig(val ip: String, val port: Int)

@Serializable
class ConfigObj(
    val repositories: List<RepositoryConfig>,
    val userManagement: List<UserManagementConfig>,
    val bind: List<BindConfig>
)

fun main(args: Array<String>) {

    val config = ConfigObj(
        repositories = listOf(
            RepositoryConfig.Docker(
                allowRewrite = true,
                allowAppend = true,
                path = "C:\\TEMP\\8",
                urlPrefix = "/myrepo",
            )
        ),
        userManagement = listOf(
            UserManagementConfig.Embedded(
                listOf(
                    UserManagementConfig.Embedded.User(
                        login = "admin",
                        password = "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918",
                    )
                )
            )
        ),
        bind = listOf(
            BindConfig(
                ip = "0.0.0.0",
                port = 7001
            )
        )
    )
    Logger.global.handler = Logger.consoleHandler
    val webLogger = Logger.getLogger("http")

    val connectionManager = NetworkDispatcher()
    val rootRouter = RootRouter()
        .wrap { action, func ->
            try {
                if (func(action)) {
                    webLogger.info("${action.req.method} ${action.req.uri} - ${action.resp.status}")
                } else {
                    webLogger.warn("Unhandled request ${action.req.method} ${action.req.uri} - ${action.resp.status}")
                }
                true
            } catch (e: SocketClosedException) {
                //IGNORE
                true
            } catch (e: Throwable) {
                webLogger.warn("Exception on ${action.req.method} ${action.req.uri} - ${action.resp.status}")
                e.printStackTrace()
                runCatching {
                    action.resp.status = 500
                }
                true
            }
        }


    val server = HttpServer(
        manager = connectionManager,
        handler = rootRouter,
        poolSize = 30,
        inputBufferSize = 1024 * 1024 * 2,
        outputBufferSize = 1024 * 1024 * 2,
        zlibBufferSize = 0
    )


    try {
        val initFuture = async2 {
            Strong.create(
                StrongConfiguration.mainConfiguration(config),
                Strong.config { strong ->

                    strong.define(connectionManager)
                    strong.define(rootRouter)
                    strong.define(server)
                    config.bind.forEach {
                        server.bindHTTP(
                            NetworkAddress.Immutable(
                                host = it.ip,
                                port = it.port,
                            )
                        )
                    }
                }).start()
        }

//        if (args.any { it == "-h" }) {
//            printHelp()
//            return
//        }
//
//        val title = args.getParam("-title") ?: "Binom Repository Server"
//        val root = (args.getParam("-root") ?: "./").let { File(it) }
//        val allowRewriting = args.getParam("-allowRewriting")?.let { it == "true" || it == "1" } ?: false
//        val enableZlib = args.getParam("-zlib")?.let { it == "true" || it == "1" } ?: false
//        val allowAnonymous = args.getParam("-allowAnonymous")?.let { it == "true" || it == "1" } ?: false
//        val webdavEnable = args.getParam("-webdav")?.let { it == "true" || it == "1" } ?: false
//        val prefix = args.getParam("-prefix") ?: ""
//        val users = ArrayList<User>()
//        val existUsers = HashSet<String>()
//
//        args.getParams("-admin").map {
//            val items = it.split(':', limit = 2)
//            if (items[0] in existUsers) {
//                LOG.warn("User \"${items[0]}\" already exist")
//                return@map
//            }
//            existUsers += items[0]
//            users += User(login = items[0], password = items[1], readOnly = false)
//        }
//
//        args.getParams("-guest").map {
//            val items = it.split(':', limit = 2)
//            if (items[0] in existUsers) {
//                LOG.warn("User \"${items[0]}\" already exist")
//                return@map
//            }
//            existUsers += items[0]
//            users += User(login = items[0], password = items[1], readOnly = true)
//        }
//
//        if (!root.isDirectory) {
//            println("Can't find root directory $root")
//            return
//        }
//
//        val bind = args.getParam("-bind")?.let {
//            val addr = RemoteAddr.parse(it)
//            if (addr == null) {
//                LOG.severe("-bind argument \"$it\" invalid")
//                return
//            }
//            addr
//        }
//
//        if (bind == null) {
//            println("Invalid -bind argument")
//            printHelp()
//            return
//        }
//
//        val config = Config(
//                root = root,
//                allowGuest = allowAnonymous,
//                allowRewriting = allowRewriting,
//                users = users,
//                prefix = prefix,
//                webdavEnable = webdavEnable,
//                title = title
//        )
//
//        LOG.info("Start Binom Repository")
//        LOG.info("Root directory: ${config.root.path}")
//        LOG.info("Allow Rewriting: ${config.allowRewriting}")
//        LOG.info("Allow Anonymous Access: ${config.allowGuest}")
//        LOG.info("Bind: $bind")
//        LOG.info("Title: ${config.title}")
//        LOG.info("Prefix: $prefix")
//        LOG.info("ZLib Enable: $enableZlib")
//        LOG.info("WebDav: ${if (config.webdavEnable) "enabled" else "disabled"}")
//
//        if (config.users.isNotEmpty()) {
//            LOG.info("Users:")
//            config.users.forEach {
//                if (it.readOnly)
//                    LOG.info("  ${it.login} [Guest]")
//                else
//                    LOG.info("  ${it.login} [Administrator]")
//            }
//        }
//
//        val bufferPool = ByteBufferPool(10)
//        val server = HttpServer(
//            manager = connectionManager,
//            handler = HttpHandler(config, bufferPool),
//            poolSize = 30,
//            inputBufferSize = 1024 * 1024 * 2,
//            outputBufferSize = 1024 * 1024 * 2,
//            zlibBufferSize = if (enableZlib) DEFAULT_BUFFER_SIZE else 0
//        )

//        server.bindHTTP(NetworkAddress.Immutable(
//            host = bind.host.takeIf { it.isNotBlank() } ?: "0.0.0.0",
//            port = bind.port
//        ))
        while (!Signal.isInterrupted) {
            if (initFuture.isDone && initFuture.isFailure) {
                throw initFuture.exceptionOrNull!!
            }
            connectionManager.select(1000)
        }
        LOG.info("Stop the Server")

        server.close()
        connectionManager.close()
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}

fun Strong.initializing(func: suspend () -> Unit) = define(object : Strong.InitializingBean {
    override suspend fun init() {
        func()
    }
})

fun Strong.linking(func: suspend () -> Unit) = define(object : Strong.LinkingBean {
    override suspend fun link() {
        func()
    }
})

