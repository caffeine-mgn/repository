package pw.binom.repo


private fun contentTypeByExt(ext: String) =
    when (ext.lowercase()) {
        "zip" -> "application/zip"
        "pom", "xml" -> "application/xml"
        "svg" -> "image/svg+xml"
        "js" -> "application/javascript"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "md" -> "text/markdown"
        else -> "application/octet-stream"
    }

//class FileSystemHandler(val title: String, val fs: FileSystem, val copyBuffer: ObjectPool<ByteBuffer>) : Handler {
//    private val log = Logger.getLogger("FileSystem")
//    private suspend fun getFile(file: FileSystem.Entity, onlyHeader: Boolean, resp: HttpResponse) {
//        if (!file.isFile)
//            throw IllegalArgumentException("File ${file.path} must be a File")
//
//        resp.status = 200
//        val ext = file.extension.toLowerCase()
//        resp.enableCompress = when (ext) {
//            "zip", "rar", "7z", "jar", "war", "gz", "tgz", "bz2", "xz" -> false
//            else -> true
//        }
//        resp.resetHeader(Headers.CONTENT_LENGTH, file.length.toString())
//        resp.resetHeader(Headers.CONTENT_TYPE, contentTypeByExt(file.extension))
//
//        if (!onlyHeader) {
//            val outStream = resp.complete().asReference()
//            execute {
//                file.read()!!.use {
//                    val buffer = ByteBuffer.alloc(DEFAULT_BUFFER_SIZE)
//                    while (true) {
//                        val r = it.read(buffer)
//                        if (r == 0) {
//                            break
//                        }
//                        network {
//                            outStream.value.write(buffer)
//                            outStream.value.flush()
//                        }
//                    }
//                }
//            }
//            outStream.free()
//        }
//    }
//
//    private suspend fun getDirList(user: BasicAuth?, onlyHeader: Boolean, file: FileSystem.Entity, resp: HttpResponse) {
//        file.fileSystem.isSupportUserSystem
//        file.fileSystem.useUser(user) {
//            if (file.isFile)
//                throw IllegalArgumentException("File ${file.path} must be a Directory")
//            resp.status = 200
//
//            resp.resetHeader(Headers.CONTENT_TYPE, "text/html; charset=UTF-8")
//            if (!onlyHeader) {
//                val sb = resp.complete().utf8Appendable()
//                sb.append("<html>")
//                    .append("<b>$title</b>").append("<hr/>")
//                sb.append("<table>")
//                    .append("<tr><td><b>Name</b></td><td><b>Size</b></td></tr>")
//                fs.getDir(file.path)!!.forEach {
//                    sb.append("<tr>")
//                    if (it.isFile)
//                        sb.append("<td><a href=\"${UTF8.urlEncode(it.name)}\">").append(it.name).append("</a></td><td>")
//                            .append(it.length.toString()).append("</td>")
//                    else
//                        sb.append("<td><a href=\"${UTF8.urlEncode(it.name)}/\">").append(it.name)
//                            .append("</a></td><td></td>")
//                    sb.append("</tr>")
//                }
//                sb.append("</table>")
//
//                sb.append("</html>")
//            }
//        }
//    }
//
//    @OptIn(ExperimentalTime::class)
//    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
//        try {
//            val user = req.basicAuth
//            fs.useUser(user) {
//                when (req.method) {
//                    "HEAD", "GET" -> {
//                        val e = fs.get(UTF8.urlDecode(req.contextUri))
//                        if (e == null) {
//                            println("NOT FOUND ${req.method} ${req.contextUri}")
//                            resp.status = 404
//                            return@useUser
//                        }
//                        resp.status = 200
//                        val onlyHeader = req.method == "HEAD"
//                        if (e.isFile) {
//                            getFile(file = e, onlyHeader = onlyHeader, resp = resp)
//                        } else {
//                            getDirList(user = user, onlyHeader = onlyHeader, file = e, resp = resp)
//                        }
//                        return@useUser
//                    }
//                    "PUT" -> {
//                        val time = measureTime {
//                            log.info("Upload ${req.contextUri}")
//                            val path = UTF8.urlDecode(req.contextUri)
//                            fs.new(path).use {
//                                req.input.copyTo(it, copyBuffer)
//                            }
//                        }
//                        log.info("File uploaded ${req.contextUri}, time: $time")
//                        resp.status = 200
//                        resp.complete()
//                        return@useUser
//                    }
//                }
//            }
//        } catch (e: FileSystemAccess.AccessException.ForbiddenException) {
//            resp.status = 403
//        } catch (e: FileSystemAccess.AccessException.UnauthorizedException) {
//            println("UnauthorizedException")
//            resp.requestBasicAuth()
//        } catch (e: Throwable) {
//            e.printStackTrace()
//        }
//
//    }
//}