package pw.binom.repo

import pw.binom.asUTF8String
import pw.binom.base64.Base64
import pw.binom.io.httpServer.HttpRequest

class BasicAuth(val login: String, val password: String) {
    companion object {
        fun get(req: HttpRequest): BasicAuth? {
            val authorization = req.headers["Authorization"]?.singleOrNull() ?: return null
            if (!authorization.startsWith("Basic "))
                return null
            val decodedBuffer = Base64.decode(authorization.removePrefix("Basic "))
            val sec = decodedBuffer.asUTF8String()
            decodedBuffer.close()
            val items = authorization.removePrefix("Basic ").let { sec }.split(':', limit = 2)
            return BasicAuth(login = items[0], password = items[1])
        }
    }
}