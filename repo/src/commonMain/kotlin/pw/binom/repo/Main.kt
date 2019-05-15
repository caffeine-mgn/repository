package pw.binom.repo

import pw.binom.Base64
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.Thread
import pw.binom.asUTF8String
import pw.binom.io.copyTo
import pw.binom.io.file.*
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.httpServer.HttpResponse
import pw.binom.io.httpServer.HttpServer
import pw.binom.io.use
import pw.binom.io.write

val LOG = Logger.getLog("Main")

class HttpHandler(private val config: Config) : Handler {

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {

        fun checkAccess(): User? {
            val authorization = req.headers["Authorization"]?.firstOrNull()
            if (authorization == null) {
                resp.status = 401
                resp.resetHeader("WWW-Authenticate", "Basic")
                LOG.warn("Authorization not set")
                return null
            }

            if (!authorization.startsWith("Basic ")) {
                LOG.warn("Invalid Authorization Type")
                resp.resetHeader("WWW-Authenticate", "Basic")
                resp.status = 401
                return null
            }

            val auth = authorization.removePrefix("Basic ").let { Base64.decode(it) }.asUTF8String().split(':', limit = 2)
            val user = config.users.find { it.login == auth[0] }
            if (user == null) {
                LOG.warn("User \"${auth[0]}\" not found")
                resp.resetHeader("WWW-Authenticate", "Basic")
                resp.status = 401
                return null
            }

            if (user.password != auth[1]) {
                LOG.warn("Invalid password of user \"${auth[0]}\"")
                resp.resetHeader("WWW-Authenticate", "Basic")
                resp.status = 401
                return null
            }
            return user
        }

        if (req.method == "GET" || req.method == "HEAD") {
            val head = req.method == "HEAD"
            if (!config.allowGuest && checkAccess() == null)
                return

            val file = if (req.uri == "/")
                config.root
            else
                File(config.root, req.uri.removePrefix("/").replace('/', File.SEPARATOR))

            if (file.isDirectory) {
                resp.status = 200
                val sb = StringBuilder()
                sb.append("<html>")
                sb.append("<b>Binom Repository Server</b>").append("<hr/>")
                file.iterator().use {
                    it.forEach {
                        if (it.isFile)
                            sb.append("<a href=\"${it.name}\">")
                        if (it.isDirectory)
                            sb.append("<a href=\"${it.name}/\">")
                        sb.append(it.name).append("</a></br>")
                    }
                }

                sb.append("</html>")
                resp.resetHeader("Content-Length", sb.length.toString())
                if (!head)
                    resp.output.write(sb.toString())
                return
            }

            if (file.isFile) {


                resp.status = 200
                resp.resetHeader("Content-Length", file.size.toString())
                val contentType = when (file.nameWithoutExtension.toLowerCase()) {
                    "zip" -> "application/zip"
                    "pom", "xml" -> "application/xml"
                    "svg" -> "image/svg+xml"
                    "js" -> "application/javascript"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
                LOG.info("Get file \"$file\". Size: ${file.size}")
                resp.resetHeader("Content-Type", contentType)
                if (req.method == "GET")
                    FileInputStream(file).use {
                        it.copyTo(resp.output, DEFAULT_BUFFER_SIZE)
                    }
                LOG.info("File \"$file\" was send")
                return
            }
            LOG.warn("Path not found \"${req.uri}\"")
            resp.status = 404
            return
        }

        if (req.method == "PUT") {
            val user = checkAccess() ?: return
            if (user.readOnly) {
                resp.status = 403
                return
            }

            val file = File(config.root, req.uri.removePrefix("/").replace('/', File.SEPARATOR))
            if (file.isFile) {

                val fileName = File(req.uri).name.toLowerCase()
                if (!config.allowRewriting &&
                        fileName != "maven-metadata.xml" &&
                        fileName != "maven-metadata.xml.md5" &&
                        fileName != "maven-metadata.xml.sha1") {

                    resp.status = 403
                    resp.disconnect()
                    LOG.warn("Not allow rewrite file $file")
                    return
                }
            }
            val startUpload = Thread.currentTimeMillis()
            file.parent.mkdirs()
            FileOutputStream(file).use {
                req.input.copyTo(it, DEFAULT_BUFFER_SIZE)
                it.flush()
            }
            LOG.info("New File Upload ${file.path} ${file.size} bytes (${Thread.currentTimeMillis() - startUpload} ms)")
            resp.status = 200
            return
        }

        resp.status = 404
    }
}

fun File.mkdirs(): Boolean {
    if (isFile)
        return false
    if (isDirectory)
        return true
    if (!parent.mkdirs())
        return false
    return mkdir()
}

class User(val login: String, val password: String, val readOnly: Boolean)

class Config(
        val root: File,
        val allowRewriting: Boolean,
        val allowGuest: Boolean,
        val users: List<User>
)

private fun printHelp() {
    println("Commands:")
    println("-root=D:\\repo    Root directory for repository")
    println("-allowRewriting=true    Allow or Disallow file rewriting")
    println("-allowAnonymous=true    Allow or Disallow Anonymous read access")
    println("-bind=0.0.0.0:8080    Bind address for web server")
    println("-admin=admin:admin123    Define new Administrator. Can upload change repository. ")
    println("-guest=admin:admin123    Define new Guest. Can only read repository. ")
    println("-h    Shows this help")
}


fun main(args: Array<String>) {
    if (args.any { it == "-h" }) {
        printHelp()
        return
    }

    val root = (args.getParam("-root") ?: "./").let { File(it) }
    val allowRewriting = args.getParam("-allowRewriting")?.let { it == "true" || it == "1" } ?: false
    val allowAnonymous = args.getParam("-allowAnonymous")?.let { it == "true" || it == "1" } ?: false
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
//    LOG.info("Thread Count: $threads")
    if (users.isNotEmpty()) {
        LOG.info("Users:")
        users.forEach {
            if (it.readOnly)
                LOG.info("${it.login} [Guest]")
            else
                LOG.info("${it.login} [Administrator]")
        }

    }
    val config = Config(
            root = root,
            allowGuest = allowAnonymous,
            allowRewriting = allowRewriting,
            users = users
    )
    val server = HttpServer(HttpHandler(config))

    server.bind(host = bind.host.takeIf { it.isNotBlank() } ?: "0.0.0.0", port = bind.port)
    while (true) {
        val r = server.update()
    }
}



