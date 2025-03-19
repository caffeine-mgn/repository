package pw.binom.repo

import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pw.binom.Environment
import pw.binom.Platform
import pw.binom.availableProcessors
import pw.binom.io.file.File
import pw.binom.io.file.readText
import pw.binom.io.file.workDirectoryFile
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.httpClient.*
import pw.binom.logger.Logger
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.platform
import pw.binom.properties.ini.addIni
import pw.binom.repo.properties.ProxyProperties
import pw.binom.repo.services.ConfigService
import pw.binom.repo.services.HttpServerService
import pw.binom.repo.services.MavenRepositoriesService
import pw.binom.signal.Signal
import pw.binom.strong.Strong
import pw.binom.strong.bean
import pw.binom.strong.properties.StrongProperties
import pw.binom.strong.properties.yaml.addYaml
import pw.binom.url.isWildcardMatch

val LOG = Logger.getLogger("Main")

private fun printHelp() {
    val simplePathToFile = when (Environment.platform) {
        Platform.MINGW_X86,
        Platform.MINGW_X64,
            -> "D:\\repository"

        Platform.JVM,
        Platform.LINUX_64,
        Platform.LINUX_ARM_64,
        Platform.LINUX_ARM_32,
            -> "/var/repository"

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

fun StrongProperties.addYaml(
    file: File,
    yaml: Yaml = Yaml(configuration = YamlConfiguration(anchorsAndAliases = AnchorsAndAliases.Permitted())),
): StrongProperties {
    if (!file.isFile) {
        return this
    }
    addYaml(file.readText(), yaml = yaml)
    return this
}

fun StrongProperties.addIni(
    file: File,
): StrongProperties {
    if (!file.isFile) {
        return this
    }
    addIni(file.readText())
    return this
}

fun ProxyProperties.Proxy.toConfig(): ProxyHttpSocketFactory.ProxySelector {
    val proxyConfig = ProxyHttpSocketFactory.ProxyConfig(
        address = address,
        auth = this.auth?.let {
            when {
                it.bearerAuth != null -> BearerAuth(it.bearerAuth.token)
                it.basicAuth != null -> BasicAuth(
                    login = it.basicAuth.user,
                    password = it.basicAuth.password
                )

                else -> null
            }
        }
    )
    return ProxyHttpSocketFactory.ProxySelector { host, port ->
        when {
            onlyFor != null -> if (host in onlyFor) {
                proxyConfig
            } else {
                if (onlyFor.any { host.isWildcardMatch(it) }) {
                    proxyConfig
                } else {
                    null
                }
            }

            noProxy != null -> if (host in noProxy) {
                null
            } else {
                if (noProxy.any { host.isWildcardMatch(it) }) {
                    null
                } else {
                    proxyConfig
                }
            }

            else -> null
        }
    }
}

fun main(args: Array<String>) {
    runBlocking {
        val properties = StrongProperties()
            .addYaml(Environment.workDirectoryFile.relative("config.yaml"))
            .addYaml(Environment.workDirectoryFile.relative("config.yml"))
            .addIni(Environment.workDirectoryFile.relative("config.ini"))
            .addArgs(args)
            .addEnvironment()
        val proxyParams = properties.parse(ProxyProperties.serializer())
        // properties.properties.getByPath("repo.repositories.maven.opt1.http.url")

        val proxyConfig =
            proxyParams.proxy?.let { proxyConfig ->
                HttpProxyConfig(
                    address = proxyConfig.address,
                    auth =
                        proxyConfig.auth?.let { auth ->
                            when {
                                auth.basicAuth != null ->
                                    BasicAuth(
                                        login = auth.basicAuth.user,
                                        password = auth.basicAuth.password
                                    )

                                auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                                else -> null
                            }
                        }
                )
            }

        val nm = MultiFixedSizeThreadNetworkDispatcher(Environment.availableProcessors)

        val proxyConfig2 = proxyParams.proxy?.toConfig()

        val httpFactory = Http11ConnectionFactory()
        val httpsFactory = Https11ConnectionFactory(fallback = httpFactory)
        var factory: NetSocketFactory = NativeNetChannelFactory(nm)
        if (proxyConfig2 != null) {
            factory = ProxyHttpSocketFactory(
                default = factory,
                proxySelector = proxyConfig2,
            )
        }
        HttpClientRunnable(
            factory = httpsFactory,
            idleCoroutineContext = nm,
            source = factory,
        )
        val strong = Strong.create(
            Strong.config { it.bean { properties } },
//            WebConfig.apply(properties),
            Strong.config {
//                it.bean { MavenHandler(FileSystemMavenRepository(File("/tmp/bb"))) }
                it.bean { nm }
                it.bean { HttpServerService() }
                it.bean { MavenRepositoriesService() }
                it.bean { ConfigService() }
                it.bean {
                    HttpClientRunnable(
                        factory = httpsFactory,
                        idleCoroutineContext = nm,
                        source = factory,
                    )
                }
                it.bean { HttpClient.create(networkDispatcher = nm, proxy = proxyConfig) }
            }
        )

        Signal.handler {
            if (it.isInterrupted) {
                GlobalScope.launch {
                    strong.destroy()
                }
            }
        }
        strong.awaitDestroy()
    }
    /*
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
    */
}

