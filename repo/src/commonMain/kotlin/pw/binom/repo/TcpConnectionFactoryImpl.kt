package pw.binom.repo
/*
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.SafeException
import pw.binom.io.*
import pw.binom.io.http.*
import pw.binom.io.httpClient.protocol.v11.Http11
import pw.binom.io.httpClient.protocol.v11.Http11ConnectFactory2
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.io.socket.SocketAddress
import pw.binom.logger.Logger
import pw.binom.logger.info
import pw.binom.network.NetworkManager
import pw.binom.network.tcpConnect
import pw.binom.repo.properties.ProxyProperties
import pw.binom.strong.inject
import pw.binom.url.isWildcardMatch

class TcpConnectionFactoryImpl(
    val proxyProperties: ProxyProperties,
) : TcpConnectionFactory {
    //    private val runtimeProperties by inject<GatewayRuntimeProperties>()
    private val networkManager by inject<NetworkManager>()
    private val logger by Logger.ofThisOrGlobal
    override suspend fun connect(host: String, port: Int): AsyncChannel {
        val address = DomainSocketAddress(
            host = host,
            port = port,
        )

        val proxyConnect = proxyProperties.proxy?.let PROXY@{ proxy ->
            proxy.noProxy?.let { noProxy ->
                if (noProxy.any { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            proxy.onlyFor?.let { onlyFor ->
                if (onlyFor.none { host.isWildcardMatch(it) }) {
                    return@PROXY null
                }
            }
            networkManager.tcpConnectViaHttpProxy(
                proxy = proxy.address.resolve(),
                address = address,
                auth = proxy.auth?.let { auth ->
                    when {
                        auth.basicAuth != null ->
                            BasicAuth(
                                login = auth.basicAuth.user,
                                password = auth.basicAuth.password
                            )

                        auth.bearerAuth != null -> BearerAuth(token = auth.bearerAuth.token)
                        else -> null
                    }
                },
                readBufferSize = proxy.bufferSize,
            )
        }
        if (proxyConnect != null) {
            logger.info("Connect to $host:$port using proxy")
            return proxyConnect
        }
        logger.info("Connect to $host:$port without proxy")
        return networkManager
            .tcpConnect(
                address = DomainSocketAddress(
                    host = host,
                    port = port
                ).resolve(),
            )
    }
}

suspend fun NetworkManager.tcpConnectViaHttpProxy(
    proxy: SocketAddress,
    address: DomainSocketAddress,
    readBufferSize: Int = DEFAULT_BUFFER_SIZE,
    auth: HttpAuth? = null,
    headers: Headers = emptyHeaders(),
) = tcpConnect(address = proxy.resolve())
    .tcpConnectViaHttpProxy(
        address = address,
        readBufferSize = readBufferSize,
        auth = auth,
        headers = headers,
    )

suspend fun AsyncChannel.tcpConnectViaHttpProxy(
    address: SocketAddress,
    readBufferSize: Int = DEFAULT_BUFFER_SIZE,
    auth: HttpAuth? = null,
    headers: Headers = emptyHeaders(),
): AsyncChannel {
    val request = "${address.host}:${address.port}"
    val headersForSend = HashHeaders2()
    headersForSend[Headers.HOST] = request
    if (auth != null) {
        headersForSend[Headers.PROXY_AUTHORIZATION] = auth.headerValue
    }
    headersForSend += headers
    bufferedAsciiWriter(closeParent = false).useAsync { writer ->
        Http11.sendRequest(
            output = writer,
            method = "CONNECT",
            request = request,
            headers = headersOf(Headers.HOST to request),
        )
    }
    val reader = bufferedAsciiReader(bufferSize = readBufferSize)
    val resp = try {
        Http11ConnectFactory2.readResponse(reader)
    } catch (e: Throwable) {
        reader.asyncCloseAnyway()
        throw e
    }
    if (resp.responseCode != 200) {
        reader.asyncClose()
        asyncClose()
        throw IOException("Can't connect via http proxy: invalid response ${resp.responseCode}")
    }
    return AsyncChannel.create(
        input = reader,
        output = this@tcpConnectViaHttpProxy,
    ) {
        reader.asyncClose()
        this@tcpConnectViaHttpProxy.asyncClose()
    }
}

 */