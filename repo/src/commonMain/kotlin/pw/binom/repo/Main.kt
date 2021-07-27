package pw.binom.repo

import JsonUtils
import kotlinx.serialization.json.Json
import pw.binom.Environment
import pw.binom.Platform
import pw.binom.flux.RootRouter
import pw.binom.io.bufferedAsciiReader
import pw.binom.io.file.File
import pw.binom.io.file.read
import pw.binom.io.readText
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.platform
import pw.binom.strong.Strong
import pw.binom.strong.StrongApplication
import pw.binom.strong.bean

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
            it.bean { ConfigService(config) }
            it.bean { config }
            it.bean { RootRouter() }
        },
        StrongConfiguration.web(),
        StrongConfiguration.mainConfiguration(),
    )
}

