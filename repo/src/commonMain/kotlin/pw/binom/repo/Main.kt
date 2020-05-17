package pw.binom.repo

import pw.binom.Environment
import pw.binom.Platform
import pw.binom.io.file.File
import pw.binom.io.file.LocalFileSystem
import pw.binom.io.httpServer.*
import pw.binom.io.socket.ConnectionManager
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.severe
import pw.binom.logger.warn
import pw.binom.platform

val LOG = Logger.getLog("Main")

class HttpHandler(private val config: Config) : Handler {
    val fs = LocalFileSystem(config.root, RepoFileSystemAccess(config))
    val dav = WebDavHandler("${config.prefix}/dav", fs)
    val files = FileSystemHandler(
            title = config.title,
            fs = fs
    )

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        if (config.webdavEnable && (req.uri == "/dav" || req.uri.startsWith("/dav/"))) {
            dav.request(
                    req.withContextURI(req.uri.removePrefix("/dav")),
                    resp
            )
            return
        }
        files.request(req, resp)
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
        Platform.LINUX_ARM_32 -> "/var/repository"

        Platform.JVM,
        Platform.JS -> ""
    }

    println("Commands:")
    println("-root=$simplePathToFile    Root directory for repository")
    println("-allowRewriting=true    Allow or Disallow file rewriting")
    println("-allowAnonymous=true    Allow or Disallow Anonymous read access")
    println("-bind=0.0.0.0:8080    Bind address for web server")
    println("-admin=admin:admin123    Define new Administrator. Can upload change repository. ")
    println("-guest=admin:admin123    Define new Guest. Can only read repository. ")
    println("-prefix=/release    Set URI prefix")
    println("-webdav=true    Enable Web Dav Access")
    println("-title=\"Binom Repository Server\" Title on File List Page")
    println("-h    Shows this help")
}


fun main(args: Array<String>) {
    if (args.any { it == "-h" }) {
        printHelp()
        return
    }

    val title = args.getParam("-title") ?: "Binom Repository Server"
    val root = (args.getParam("-root") ?: "./").let { File(it) }
    val allowRewriting = args.getParam("-allowRewriting")?.let { it == "true" || it == "1" } ?: false
    val allowAnonymous = args.getParam("-allowAnonymous")?.let { it == "true" || it == "1" } ?: false
    val webdavEnable = args.getParam("-webdav")?.let { it == "true" || it == "1" } ?: false
    val prefix = args.getParam("-prefix") ?: ""
    val users = ArrayList<User>()
    val existUsers = HashSet<String>()

    args.getParams("-admin").map {
        val items = it.split(':', limit = 2)
        if (items[0] in existUsers) {
            LOG.warn("User \"${items[0]}\" already exist")
            return@map
        }
        existUsers += items[0]
        users += User(login = items[0], password = items[1], readOnly = false)
    }

    args.getParams("-guest").map {
        val items = it.split(':', limit = 2)
        if (items[0] in existUsers) {
            LOG.warn("User \"${items[0]}\" already exist")
            return@map
        }
        existUsers += items[0]
        users += User(login = items[0], password = items[1], readOnly = true)
    }

    if (!root.isDirectory) {
        println("Can't find root directory $root")
        return
    }

    val bind = args.getParam("-bind")?.let {
        val addr = RemoteAddr.parse(it)
        if (addr == null) {
            LOG.severe("-bind argument \"$it\" invalid")
            return
        }
        addr

    }

    if (bind == null) {
        println("Invalid -port argument")
        printHelp()
        return
    }

    LOG.info("Start Binom Repository")
    LOG.info("Root directory: $root")
    LOG.info("Allow Rewriting: $allowRewriting")
    LOG.info("Allow Anonymous Access: $allowAnonymous")
    LOG.info("Bind: $bind")
    LOG.info("Prefix: $prefix")
    LOG.info("WebDav: ${if (webdavEnable) "enabled" else "disabled"}")
//    LOG.info("Thread Count: $threads")
    if (users.isNotEmpty()) {
        LOG.info("Users:")
        users.forEach {
            if (it.readOnly)
                LOG.info("  ${it.login} [Guest]")
            else
                LOG.info("  ${it.login} [Administrator]")
        }

    }
    val config = Config(
            root = root,
            allowGuest = allowAnonymous,
            allowRewriting = allowRewriting,
            users = users,
            prefix = prefix,
            webdavEnable = webdavEnable,
            title = title
    )
    val connectionManager = ConnectionManager()
    val server = HttpServer(connectionManager, HttpHandler(config))

    server.bindHTTP(host = bind.host.takeIf { it.isNotBlank() } ?: "0.0.0.0", port = bind.port)
    while (true) {
        val r = server.update()
    }
}



