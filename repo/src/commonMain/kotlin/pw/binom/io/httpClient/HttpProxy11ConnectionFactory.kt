package pw.binom.io.httpClient
/*
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.SafeException
import pw.binom.io.*
import pw.binom.io.http.*
import pw.binom.io.httpClient.protocol.v11.Http11
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.io.socket.SocketAddress
import pw.binom.url.URL

class HttpProxy11ConnectionFactory(
    private val dest: HttpConnectionFactory,
    private val proxySelector: ProxySelector,
) : HttpConnectionFactory {

    fun interface ProxySelector {
        fun getProxyConfig(url: URL): ProxyConfig?
    }

    data class ProxyConfig(
        val address: SocketAddress,
        val auth: HttpAuth? = null,
        val headers: Headers = emptyHeaders(),
    )

    override suspend fun connect(
        url: URL,
        source: NetSocketFactory,
        pushBack: suspend (HttpConnection) -> Unit
    ): HttpConnection {

        val proxyAddress = proxySelector.getProxyConfig(url = url)
            ?: return dest.connect(url = url, source = source, pushBack = pushBack)
        val port = url.port ?: dest.getDefaultPort(url.schema)
        ?: throw IllegalStateException("Can't connect to $url: Unknown default port for url")
        return SafeException.async {
            val channel = source.connect(
                host = proxyAddress.address.host,
                port = proxyAddress.address.port
            ).closeOnException()
            val proxyChannel = channel.tcpConnectViaHttpProxy(
                address = DomainSocketAddress(host = url.domain, port = port),
                auth = proxyAddress.auth,
                headers = proxyAddress.headers,
            )
        }
    }
}
*/