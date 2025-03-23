package pw.binom.repo

import pw.binom.io.httpServer.HttpHandler
import pw.binom.io.httpServer.HttpServerExchange
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.url.Path
import pw.binom.url.toPath

abstract class AbstractHttpHandler : HttpHandler {
    private val hostFilter = HashSet<String>()
    private val methodFilter = HashSet<String>()
    private val urlPrefixFilter = HashSet<String>()
    private val logger by Logger.ofThisOrGlobal
    fun addHostFilter(host: String) {
        hostFilter += host
    }

    fun addMethodFilter(host: String) {
        methodFilter += host
    }

    fun addUrlPrefixFilter(host: String) {
        urlPrefixFilter += host
    }

    override suspend fun handle(exchange: HttpServerExchange) {
        var exchange = exchange
        if (hostFilter.isNotEmpty()) {
            val host = exchange.requestHeaders.getSingleOrNull("Host") ?: return
            if (host !in hostFilter) {
                return
            }
        }
        if (methodFilter.isNotEmpty()) {
            if (exchange.requestMethod !in methodFilter) {
                return
            }
        }
        logger.info("urlPrefixFilter=$urlPrefixFilter")
        logger.info("exchange.path=${exchange.path}")
        if (urlPrefixFilter.isNotEmpty()) {
            val prefix = urlPrefixFilter.find { exchange.path.startsWith(it) }
            if (prefix == null) {
                logger.info("Prefix is null :(")
                return
            }
            logger.info("prefix found!")
            exchange = OverridedExchange(
                path = exchange.path.removePrefix(prefix.toPath),
                other = exchange,
            )
        }
        logger.info("processing...")
        processing(exchange)
    }

    private class OverridedExchange(override val path: Path, other: HttpServerExchange) : HttpServerExchange by other


    protected abstract suspend fun processing(exchange: HttpServerExchange)
}