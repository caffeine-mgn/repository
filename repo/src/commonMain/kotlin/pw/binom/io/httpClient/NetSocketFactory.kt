package pw.binom.io.httpClient

import pw.binom.io.AsyncChannel

fun interface NetSocketFactory {
    suspend fun connect(host: String, port: Int): AsyncChannel
}