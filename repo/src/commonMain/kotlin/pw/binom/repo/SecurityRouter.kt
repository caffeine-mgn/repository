package pw.binom.repo

import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.use
import pw.binom.io.useAsync

class SecurityRouter(val nextRouter: Handler) : Handler {
    class NotAllowedException : RuntimeException()
    class InvalidAuthException : RuntimeException()

    override suspend fun request(req: HttpRequest) {
        try {
            nextRouter.request(req)
        } catch (e: NotAllowedException) {
            req.response().useAsync {
                it.status = 403
            }
        } catch (e: InvalidAuthException) {
            req.response().useAsync {
                it.status = 401
            }
        }
    }
}