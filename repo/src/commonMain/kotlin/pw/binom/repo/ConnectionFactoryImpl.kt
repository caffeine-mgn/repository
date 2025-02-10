package pw.binom.repo

import pw.binom.io.AsyncChannel
import pw.binom.io.httpClient.ConnectionFactory
import pw.binom.network.NetworkManager

class ConnectionFactoryImpl: ConnectionFactory {
    override suspend fun connect(channel: AsyncChannel, schema: String, host: String, port: Int): AsyncChannel {
        return super.connect(channel, schema, host, port)
    }

    override suspend fun connect(
        networkManager: NetworkManager,
        schema: String,
        host: String,
        port: Int,
    ): AsyncChannel {
        return super.connect(networkManager, schema, host, port)
    }
}