package pw.binom.repo.services

import kotlinx.coroutines.cancelAndJoin
import pw.binom.ByteBufferPool
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.io.AsyncCloseable
import pw.binom.io.ByteBufferFactory
import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServer2
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.io.socket.InetSocketAddress
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.logger.infoSync
import pw.binom.network.NetworkManager
import pw.binom.pool.GenericObjectPool
import pw.binom.repo.HttpNotFoundException
import pw.binom.repo.handlers.MavenHandler
import pw.binom.repo.properties.HttpEndpointProperty
import pw.binom.strong.BeanLifeCycle
import pw.binom.strong.inject

class HttpServerService {
    private val mavenRepositoriesService by inject<MavenRepositoriesService>()
    private val dispatcher by inject<NetworkManager>()
    private val bufferPool by BeanLifeCycle.afterInit {
        GenericObjectPool(factory = ByteBufferFactory(DEFAULT_BUFFER_SIZE))
    }

    init {
        BeanLifeCycle.preDestroy {
            bufferPool.closeAnyway()
        }
    }

    private val ports = HashMap<Int, HttpPort>()
    private val idBind = HashMap<String, HttpPort>()
    private val logger by Logger.ofThisOrGlobal

    private fun getOrCreate(port: Int): HttpPort {
        val exist = ports[port]
        if (exist != null) {
            return exist
        }
        val new = HttpPort(port = port, dispatcher = dispatcher, byteBufferPool = bufferPool)
        ports[port] = new
        return new
    }

    fun add(id: String, endpoint: HttpEndpointProperty) {
        check(!idBind.containsKey(id)) { "endpoint with id \"$id\" already exist" }
        val handler = when {
            endpoint.repository.maven != null -> {
                logger.infoSync("Listening on 0.0.0.0:${endpoint.port} maven repository \"${endpoint.repository.maven}\"")
                val repository = mavenRepositoriesService.getById(endpoint.repository.maven)
                    ?: throw IllegalStateException("Maven repository \"${endpoint.repository.maven}\" not found")
                MavenHandler(repository = repository)
            }

            else -> TODO()
        }
        endpoint.middlewares.forEach {
            when {
                it.host != null -> handler.addHostFilter(it.host)
                it.method != null -> handler.addMethodFilter(it.method)
                it.urlPrefix != null -> handler.addUrlPrefixFilter(it.urlPrefix)
            }
        }
        val port = getOrCreate(port = endpoint.port)
        port.add(id = id, handler = handler)
        idBind[id] = port
    }

    suspend fun remove(id: String) {
        val port = idBind[id] ?: return
        port.remove(id)
        if (port.handlerCount <= 0) {
            port.asyncClose()
            ports.remove(port.port)
        }
    }


    class HttpPort(
        val port: Int,
        dispatcher: NetworkManager,
        byteBufferPool: ByteBufferPool,
    ) : AsyncCloseable, HttpHandler {

        private val handlers = LinkedHashMap<String, HttpHandler>()
        val handlerCount
            get() = handlers.size

        private val ff = HttpServer2(dispatcher = dispatcher, handler = this, byteBufferPool = byteBufferPool)
        private val job = ff.listen(InetSocketAddress.resolve(host = "0.0.0.0", port = port))
        fun add(id: String, handler: HttpHandler) {
            check(!handlers.containsKey(id)) { "Handler for \"$id\" already exists" }
            handlers[id] = handler
        }

        fun remove(id: String) {
            handlers.remove(id)
        }

        override suspend fun asyncClose() {
            job.cancelAndJoin()
            ff.asyncClose()
        }

        private val logger by Logger.ofThisOrGlobal
        override suspend fun handle(exchange: HttpServerExchange) {
            logger.infoSync("Income request ${exchange.requestMethod} ${exchange.requestURI}}")
            try {
                handlers.values.forEach {
                    it.handle(exchange)
                    if (exchange.responseStarted) {
                        return
                    }
                }
                logger.info("No repository for path ${exchange.requestURI}")
            } catch (e: HttpNotFoundException) {
                e.printStackTrace()
                if (!exchange.responseStarted) {
                    exchange.startResponse(404)
                }
                return
            } catch (e: Throwable) {
                e.printStackTrace()
                if (!exchange.responseStarted) {
                    exchange.startResponse(500)
                    return
                }
            }
            if (!exchange.responseStarted) {
                exchange.startResponse(404)
            }
        }

    }
}