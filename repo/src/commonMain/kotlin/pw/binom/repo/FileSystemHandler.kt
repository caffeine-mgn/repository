package pw.binom.repo

import pw.binom.io.*
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.*

private fun contentTypeByExt(ext: String) =
        when (ext.toLowerCase()) {
            "zip" -> "application/zip"
            "pom", "xml" -> "application/xml"
            "svg" -> "image/svg+xml"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }

class FileSystemHandler(val title: String, val fs: FileSystem<BasicAuth?>) : Handler {

    private suspend fun getFile(file: FileSystem.Entity<BasicAuth?>, resp: HttpResponse) {
        if (!file.isFile)
            throw IllegalArgumentException("File ${file.path} must be a File")

        resp.status = 200
        resp.resetHeader("Content-Length", file.length.toString())
        resp.resetHeader("Content-Type", contentTypeByExt(file.extension))

        file.read()!!.use {
            it.copyTo(resp.output)
        }
    }

    private suspend fun getDirList(user: BasicAuth?, file: FileSystem.Entity<BasicAuth?>, resp: HttpResponse) {
        if (file.isFile)
            throw IllegalArgumentException("File ${file.path} must be a Directory")
        resp.status = 200

        resp.resetHeader(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")

        val sb = resp.output.utf8Appendable()
        sb.append("<html>")
                .append("<b>$title</b>").append("<hr/>")
        sb.append("<table>")
                .append("<tr><td><b>Name</b></td><td><b>Size</b></td></tr>")
        fs.getDir(user, file.path)!!.forEach {
            sb.append("<tr>")
            if (it.isFile)
                sb.append("<td><a href=\"${urlEncode(it.name)}\">").append(it.name).append("</a></td><td>").append(it.length.toString()).append("</td>")
            else
                sb.append("<td><a href=\"${urlEncode(it.name)}/\">").append(it.name).append("</a></td><td></td>")
            sb.append("</tr>")
        }
        sb.append("</table>")

        sb.append("</html>")
        resp.output.flush()
    }

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        try {
            val user = BasicAuth.get(req)
            when (req.method) {
                "GET" -> {
                    val e = fs.get(user, urlDecode(req.contextUri))
                    if (e == null) {
                        resp.status = 404
                        return
                    }
                    resp.status = 200
                    if (e.isFile) {
                        getFile(e, resp)
                    } else {
                        getDirList(user, e, resp)
                    }
                    return
                }
                "HEAD" -> {
                    val out = NullAsyncOutputStream()
                    val r = resp.withOutput(out)
                    val r2 = req.withMethod("GET")
                    request(r2, r)
                    resp.status = r.status
                    resp.resetHeaders(r)
                    return
                }
                "PUT" -> {
                    val path = urlDecode(req.contextUri)
                    fs.get(user, path) ?: fs.new(user, path).use {
                        req.input.copyTo(it)
                    }
                    resp.status = 200
                    return
                }
            }
        } catch (e: FileSystemAccess.AccessException.ForbiddenException) {
            resp.status = 403
        } catch (e: FileSystemAccess.AccessException.UnauthorizedException) {
            resp.resetHeader("WWW-Authenticate", "Basic")
            resp.status = 401
        }

    }
}

class NullAsyncOutputStream : AsyncOutputStream {
    private var closed = false
    private var _wrote = 0L

    val wrote: Long
        get() = _wrote

    private fun checkClose() {
        if (closed)
            throw IllegalStateException("Thread already closed")
    }

    override suspend fun close() {
        checkClose()
        closed = true
    }

    override suspend fun flush() {
        checkClose()
    }

    override suspend fun write(data: Byte): Boolean {
        checkClose()
        _wrote += 1
        return true
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int): Int {
        checkClose()
        _wrote += length
        return length
    }

}