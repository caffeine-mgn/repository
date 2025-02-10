package pw.binom.repo

import pw.binom.copyTo
import pw.binom.io.AsyncInput
import pw.binom.io.AsyncOutput
import pw.binom.io.http.BasicAuth
import pw.binom.io.http.BearerAuth
import pw.binom.io.http.HashHeaders
import pw.binom.io.http.Headers
import pw.binom.io.httpClient.*
import pw.binom.io.socket.DomainSocketAddress
import pw.binom.io.socket.SocketAddress
import pw.binom.io.use
import pw.binom.io.useAsync
import pw.binom.network.MultiFixedSizeThreadNetworkDispatcher
import pw.binom.testing.Testing
import pw.binom.url.toURL
import kotlin.test.Test

class SslTest {
    @Test
    fun aa() = Testing.async {
        MultiFixedSizeThreadNetworkDispatcher(4).use { nm ->
            val factory = NativeNetChannelFactory(nm)

            val proxy = ProxyHttpSocketFactory.ProxyConfig(
                address = DomainSocketAddress(host = "otp.xx", port = 8081),
            )

            val proxyFactory = ProxyHttpSocketFactory(
                default = factory,
            ) { host, port ->
                if (host.endsWith(".isb") || host.endsWith(".otpbank.ru")) {
                    proxy
                } else {
                    null
                }
            }
            val httpFactory = Http11ConnectionFactory()
            val httpsFactory = Https11ConnectionFactory(fallback = httpFactory)
            HttpClientRunnable(
                factory = httpsFactory,
                idleCoroutineContext = nm,
                source = proxyFactory,
            ).useAsync { client ->
                client.request(
                    method = "GET",
                    url = "https://jira.otpbank.ru/login.jsp".toURL(),
                ).connect().useAsync { con ->
                    println("Headers:\n${con.getResponseHeaders()}")
                    println("--->${con.getResponseCode()}")
                    println("Content:\n${con.reader().readText()}")
                }
            }
//            val proxy = HttpProxyConfig(
//                address = DomainSocketAddress(host = "otp.xx", port = 8081),
//            )
//            HttpClient.create(networkDispatcher = nm, proxy = proxy).use { client ->
//                client.connect(
//                    method = "GET",
//                    uri = "https://nexus.isb/repository/clp-nonprod-mvn-hosted/ru/otpbank/xsell/feignclient/shared-jvm/0.3.0.36/shared-jvm-0.3.0.36.jar".toURL()
//                ).useAsync { req ->
//                    val resp = req.getResponse()
//                    println("resp.responseCode=${resp.responseCode}")
//                    resp.readBinary().useAsync {
//                        it.copyTo(AsyncOutput.NULL)
//                    }
//                }
//            }
        }
    }
}