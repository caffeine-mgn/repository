package pw.binom.repo

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pw.binom.*
import pw.binom.concurrency.WorkerPool
import pw.binom.flux.DefaultErrorHandler
import pw.binom.flux.RootRouter
import pw.binom.flux.exceptionHandler
import pw.binom.flux.wrap
import pw.binom.io.bufferedAsciiReader
import pw.binom.io.bufferedInput
import pw.binom.io.file.File
import pw.binom.io.file.read
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
import pw.binom.strong.StrongApplication

val LOG = Logger.getLogger("Main")

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
        else -> TODO()
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

fun main(args: Array<String>) {
    val config = args.mapNotNull {
        if (it.startsWith("-config=")) {
            val txt = it.removePrefix("-config=")
            val configText = if (txt.startsWith("{")) {
                txt
            } else {
                File(txt).read().bufferedAsciiReader().use { it.readText() }
            }

            Json.parseToJsonElement(configText)
        } else {
            null
        }
    }.reduce { first, second ->
        JsonUtils.merge(first, second)
    }.let {
        Json.decodeFromJsonElement(Config.serializer(), it)
    }
//    val config = Config(
//        repositories = listOf(
//            RepositoryConfig.Docker(
//                allowRewrite = true,
//                allowAppend = true,
//                urlPrefix = "/myrepo",
//                name = "images",
//            ),
//            RepositoryConfig.Maven(
//                allowRewrite = true,
//                allowAppend = true,
//                name = "binom",
//                urlPrefix = "/binom",
//            )
//        ),
//        userManagement = listOf(
//            UserManagementConfig.Embedded(
//                listOf(
//                    UserManagementConfig.Embedded.User(
//                        login = "admin",
//                        password = "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918",
//                    ),
//                    UserManagementConfig.Embedded.User(
//                        login = "ci",
//                        password = "4fc82b26aecb47d2868c4efbe3581732a3e7cbcc6c2efb32062c08170a05eeb8",
//                    ),
//                )
//            )
//        ),
//        bind = listOf(
//            BindConfig(
//                ip = "0.0.0.0",
//                port = 7002
//            )
//        ),
//        blobStorages = listOf(
//            BlobStorage.FileBlobStorage(
//                root = "C:\\TEMP\\11\\blobs",
//                id = "35c3e36a-8ea5-49fd-8dba-190f12b81ac1"
//            )
//        ),
//        dataDir = "C:\\TEMP\\11\\repositories",
//        copyBufferSize = DEFAULT_BUFFER_SIZE
//    )
//    val configs = HashMap<String, String>()
//    args.forEach {
//        if (it.startsWith("-config=")) {
//            val configFile = File(it.removePrefix("-config="))
//            if (configFile.isFile) {
//                throw RuntimeException("Can't find config file ${configFile.path}")
//            }
//            configFile.read().bufferedAsciiReader().use {
//                it.readText().split('\n').forEach {
//                    val items = it.split('=', limit = 2)
//                    configs[items[0]] = items[1]
//                }
//            }
//        }
//    }
    println("Config:\n")
    println(Json.encodeToString(Config.serializer(), config))

//    args.forEach {
//        if (it.startsWith("-") && "=" in it && !it.startsWith("-config=")) {
//            val items = it.removePrefix("-").split('=', limit = 2)
//            configs[items[0]] = items[1]
//        }
//    }
//    Json.decodeFromString(ConfigObj.serializer(), configs)
    Logger.global.handler = Logger.consoleHandler








    StrongApplication.start(
        StrongConfiguration.web(config),
        StrongConfiguration.mainConfiguration(config),
    )
}

