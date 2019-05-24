package pw.binom.repo

import pw.binom.Base64
import pw.binom.asUTF8String
import pw.binom.io.httpServer.HttpRequest

class BasicAuth(val login: String, val password: String) {
    companion object {
        fun get(req: HttpRequest): BasicAuth? {
            val authorization = req.headers["Authorization"]?.singleOrNull() ?: return null
            if (!authorization.startsWith("Basic "))
                return null

            val items = authorization.removePrefix("Basic ").let { Base64.decode(it) }.asUTF8String().split(':', limit = 2)
            return BasicAuth(login = items[0], password = items[1])
        }
    }
}