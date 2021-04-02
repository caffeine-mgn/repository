package pw.binom.repo

import pw.binom.flux.RootRouter
import pw.binom.flux.exceptionHandler
import pw.binom.flux.head
import pw.binom.flux.wrap
import pw.binom.io.httpServer.HttpServer
import pw.binom.io.use
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkAddress
import pw.binom.network.NetworkDispatcher
import pw.binom.strong.EventSystem
import pw.binom.strong.Strong
import pw.binom.strong.defineClosable
import pw.binom.strong.inject

const val ROOT_ROUTER = "root-router"

object StrongConfiguration {

    fun web(config: Config) =
        Strong.config { strong ->
            if (config.bind.isEmpty()) {
                throw IllegalArgumentException("Bind list is empty")
            }
            val webLogger = Logger.getLogger("http")
            val repoHandler = RepositoryHandler(strong)
            val rootRouter = RootRouter()
                .wrap { action, func ->
                    func(action)
                    if (action.response == null) {
                        webLogger.info("Unhandled request ${action.method} ${action.request}")
                        action.response().use {
                            it.status = 404
                        }
                    }
                }
                .also {
//                    it.route("/repositories/*"){
//                        it.head("*"){
//                            println("!")
//                        }
//                    }
                    it.route("/repositories/*").forward(repoHandler)
                }
                .exceptionHandler { req, throwable ->
                    if (req.response == null) {
                        req.response().use {
                            webLogger.warn("Exception on ${req.method} ${req.request}", throwable)
                            it.status = 500
                        }
                    }
                }
            val connectionManager by strong.inject<NetworkDispatcher>()
            val server = HttpServer(
                manager = connectionManager,
                handler = SecurityRouter(rootRouter),
//        poolSize = 30,
//        inputBufferSize = 1024 * 1024 * 2,
//        outputBufferSize = 1024 * 1024 * 2,
//        zlibBufferSize = 0,
//        executor = WorkerPool(10)
            )

            strong.define(rootRouter, name = ROOT_ROUTER)
            config.bind.forEach {
                webLogger.info("Bind ${it.ip}:${it.port}")
                server.bindHttp(
                    NetworkAddress.Immutable(
                        host = it.ip,
                        port = it.port,
                    )
                )
            }
            strong.defineClosable(server)
        }


    fun mainConfiguration(config: Config) = Strong.config { strong ->
        strong.define(EventSystem())
        strong.define(Repo(config, strong))
        strong.define(CommonUsersService(strong))
//        val blobs = config.blobStorages.map {
//            when (it) {
//                is BlobStorage.FileBlobStorage -> FileBlobStorageService.open(
//                    root = File(it.root),
//                    id = UUID.fromString(it.id),
//                    bufferSize = config.copyBufferSize
//                )
//            }
//        }
//
//        config.userManagement.forEach {
//            val usersService = when (it) {
//                is UserManagementConfig.Embedded -> EmbeddedUsersService(it)
//                is UserManagementConfig.LDAP -> LDAPUsersService(it)
//            }
//            strong.define(usersService)
//        }
//
//        config.repositories.forEach {
//            val repo: Any = when (it) {
//                is RepositoryConfig.Docker -> DockerHandler(
////                    strong = strong,
//                    urlPrefix = it.urlPrefix,
//                    data = DockerDatabase2.open(File(config.dataDir).relative(it.name)),
////                    path = File(config.dataDir).relative(it.name),
////                    allowRewrite = it.allowRewrite,
////                    allowAppend = it.allowAppend,
//                )
//                is RepositoryConfig.Maven -> MavenRepositoryService(
//                    strong = strong,
//                    urlPrefix = it.urlPrefix,
//                    allowRewrite = it.allowRewrite,
//                    allowAppend = it.allowAppend,
//                    path = File(config.dataDir).relative(it.name),
//                )
//            }
//            strong.define(repo)
//        }
    }
}