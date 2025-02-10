package pw.binom.repo

import pw.binom.flux.RootRouter
import pw.binom.io.http.forEachHeader
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpServer
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.warn
import pw.binom.network.NetworkManager
import pw.binom.repo.controllers.RepositoryController
import pw.binom.strong.*

const val ROOT_ROUTER = "root-router"

object StrongConfiguration {

    fun web() =
        Strong.config { strong ->
//            strong.bean { WebServerService(it) }
            strong.bean { RepositoryController(it) }
        }


//    fun mainConfiguration() = Strong.config { strong ->
//        strong.bean { LocalEventSystem() }
//        strong.bean { RepositoryHandler() }
//        strong.bean { Repo() }
//        strong.bean { CommonUsersService() }
//    }
}

//class WebServerService(strong: Strong) : Strong.InitializingBean, Strong.DestroyableBean {
//
//    private val logger = Logger.getLogger("http")
//    private val flex by strong.inject<RootRouter>()
//    private val repositoryHandler by strong.inject<RepositoryHandler>()
//    private val config by strong.inject<Config>()
//    private val connectionManager by strong.inject<NetworkManager>()
//    private lateinit var server: HttpServer
//
//    override suspend fun destroy(strong: Strong) {
//        server.asyncClose()
//    }
//
//    override suspend fun init(strong: Strong) {
//        if (config.bind.isEmpty()) {
//            throw IllegalArgumentException("Bind list is empty")
//        }
//
//        flex.route("/repositories/*").forward(repositoryHandler)
//        val handler = Handler {
//            flex.execute(it)
//            if (it.response == null) {
//                logger.warn("Unhandled request ${it.method} ${it.request} - ${it.response?.status}")
//                it.headers.forEachHeader { s, s2 ->
//                    println("$s: $s2")
//                }
//                it.response {
//                    it.status = 404
//                }
//            }
//        }
//        server = HttpServer(
//            manager = connectionManager,
//            handler = SecurityRouter(handler),
//        )
//        config.bind.forEach {
//            logger.info("Bind ${it.ip}:${it.port}")
//            server.bindHttp(
//                NetworkAddress.Immutable(
//                    host = it.ip,
//                    port = it.port,
//                )
//            )
//        }
//    }
//
//}