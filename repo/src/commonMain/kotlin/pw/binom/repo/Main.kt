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

        Platform.JVM,
        Platform.MACOS,
        Platform.LINUX_64,
        Platform.LINUX_ARM_64,
        Platform.LINUX_ARM_32 -> "/var/repository"

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

    println("Config:\n")
    println(Json.encodeToString(Config.serializer(), config))


    StrongApplication.start(
        Strong.config {
            it.define(config)
            it.define(RootRouter())
        },
        StrongConfiguration.web(),
        StrongConfiguration.mainConfiguration(),
    )
}

