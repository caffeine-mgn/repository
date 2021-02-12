package pw.binom.repo

import pw.binom.flux.AbstractRoute
import pw.binom.flux.Action
import pw.binom.flux.Route
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.httpServer.HttpResponse

class SecurityRouter(val nextRouter: Handler) : Handler {
    class NotAllowedException : RuntimeException()
    class InvalidAuthException : RuntimeException()

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        try {
            nextRouter.request(req, resp)
        } catch (e: NotAllowedException) {
            resp.status = 403
        } catch (e: InvalidAuthException) {
            resp.status = 401
        }
    }
}