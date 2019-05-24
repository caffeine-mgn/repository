package pw.binom.repo

import pw.binom.io.FileSystem
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.httpServer.HttpResponse
import pw.binom.webdav.server.AbstractWebDavHandler

class WebDavHandler(val prefix: String, val fs: FileSystem<BasicAuth?>) : AbstractWebDavHandler<BasicAuth?>() {
    override fun getFS(req: HttpRequest, resp: HttpResponse): FileSystem<BasicAuth?> = fs

    override fun getGlobalURI(req: HttpRequest): String {
        return prefix
    }

    override fun getLocalURI(req: HttpRequest, globalURI: String): String = globalURI.removePrefix(prefix)

    override fun getUser(req: HttpRequest, resp: HttpResponse): BasicAuth? = BasicAuth.get(req)

}