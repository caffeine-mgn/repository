package pw.binom.repo

import pw.binom.ByteBuffer
import pw.binom.copyTo
import pw.binom.io.*
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.Handler
import pw.binom.io.httpServer.HttpRequest
import pw.binom.io.httpServer.HttpResponse
import pw.binom.pool.ObjectPool

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

class FileSystemHandler(val title: String, val fs: FileSystem<BasicAuth?>, val copyBuffer: ObjectPool<ByteBuffer>) : Handler {

    private suspend fun getFile(file: FileSystem.Entity<BasicAuth?>, onlyHeader: Boolean, resp: HttpResponse) {
        if (!file.isFile)
            throw IllegalArgumentException("File ${file.path} must be a File")

        resp.status = 200
        val ext = file.extension.toLowerCase()
        resp.enableCompress = when (ext) {
            "zip", "rar", "7z", "jar", "war", "gz", "tgz", "bz2", "xz" -> false
            else -> true
        }
        resp.resetHeader(Headers.CONTENT_LENGTH, file.length.toString())
        resp.resetHeader(Headers.CONTENT_TYPE, contentTypeByExt(file.extension))

        if (!onlyHeader)
            file.read()!!.use {
                it.copyTo(resp.complete(), copyBuffer)
            }
    }

    private suspend fun getDirList(user: BasicAuth?, onlyHeader: Boolean, file: FileSystem.Entity<BasicAuth?>, resp: HttpResponse) {
        if (file.isFile)
            throw IllegalArgumentException("File ${file.path} must be a Directory")
        resp.status = 200

        resp.resetHeader(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
        if (!onlyHeader) {
            val sb = resp.complete().utf8Appendable()
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
        }
    }

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        try {
            val user = BasicAuth.get(req)
            when (req.method) {
                "HEAD", "GET" -> {
                    val e = fs.get(user, urlDecode(req.contextUri))
                    if (e == null) {
                        resp.status = 404
                        return
                    }
                    resp.status = 200
                    val onlyHeader = req.method == "HEAD"
                    if (e.isFile) {
                        getFile(file = e, onlyHeader = onlyHeader, resp = resp)
                    } else {
                        getDirList(user = user, onlyHeader = onlyHeader, file = e, resp = resp)
                    }
                    return
                }
                "PUT" -> {
                    val path = urlDecode(req.contextUri)
                    fs.get(user, path) ?: fs.new(user, path).use {
                        req.input.copyTo(it, copyBuffer)
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