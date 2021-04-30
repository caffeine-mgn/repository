package pw.binom.repo.repositories

import pw.binom.io.AsyncCloseable
import pw.binom.io.httpServer.Handler

interface Repository : Handler, AsyncCloseable {
    suspend fun start() {

    }
}