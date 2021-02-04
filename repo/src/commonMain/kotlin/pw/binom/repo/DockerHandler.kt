package pw.binom.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pw.binom.*
import pw.binom.date.Date
import pw.binom.io.*
import pw.binom.io.file.*
import pw.binom.io.http.Headers
import pw.binom.io.httpServer.*
import pw.binom.network.SocketClosedException
import pw.binom.network.execute
import pw.binom.pool.ObjectPool
import kotlin.random.Random

@Serializable
class ErrorDescription(val code: String, val message: String, val detail: String?)

@Serializable
class DockerResponse(
    val errors: List<ErrorDescription>
)

class DockerHandler(val config: Config, val pool: ByteBufferPool) : Handler {

    private val patchs = HashMap<UUID, String>()

    private suspend fun checkUser(req: HttpRequest, resp: HttpResponse): Pair<Boolean, User?> {
        val auth = req.basicAuth
        val modify = req.method == "POST" || req.method == "PUT"
        if (auth == null) {
            if (modify) {
                resp.requestBasicAuth()
                resp.complete().bufferedWriter(closeParent = false).use {
                    it.append(
                        generateUnauthorized(msg = "access to write resource is not authorized")
                    )
                    it.flush()
                }
                return false to null
            }
            if (!config.allowGuest) {
                resp.requestBasicAuth()
                resp.complete().bufferedWriter(closeParent = false).use {
                    it.append(
                        generateUnauthorized(msg = "Guest access not allowed")
                    )
                    it.flush()
                }
                return false to null
            }
        } else {
            val user = config.users.find { it.login == auth.login && it.password == auth.password }
            if (user == null) {
                resp.requestBasicAuth()
                resp.complete().bufferedWriter().also {
                    it.append(
                        generateUnauthorized("User not found")
                    )
                    it.flush()
                }
                return false to null
            }
            return true to user
        }

        return true to null
    }

    private suspend fun blobProcessing(user: User?, req: HttpRequest, resp: HttpResponse) {
        val p = req.contextUri.lastIndexOf("/blobs/")
        if (p < 0) {
            println("invalid path! ${req.contextUri}")
            resp.status = 404
            resp.complete()
            return
        }
        val image = req.contextUri.substring(1, p)
        val blobPath = req.contextUri.substring(p + 6)
        val imageDir = config.root.relative(image)
        val blobDir = imageDir.relative("blobs")
        blobDir.mkdirs()
        if (blobPath.startsWith("/sha256:")) {
            val blobFile = blobDir.relative(blobPath.removePrefix("/sha256:"))
            if (req.method == "HEAD") {
                if (blobFile.isExist) {
                    resp.resetHeader("docker-content-digest", "sha256:${blobFile.name}")
                    resp.resetHeader("docker-distribution-api-version", "registry/2.0")
                    resp.resetHeader("date", Date(blobFile.lastModified).calendar(0).toString())
                    resp.resetHeader("content-type", "application/vnd.docker.image.rootfs.diff.tar.gzip")
                    resp.resetHeader(Headers.CONTENT_LENGTH, blobFile.size.toULong().toString())
                    resp.status = 200
                    return
                } else {
                    println("File: ${req.contextUri} not found")
                    resp.status = 404
                    return
                }
            }
            if (req.method == "POST") {
                if (blobFile.isExist) {
                    if (user?.readOnly == true && !config.allowRewriting) {
                        resp.status = 403
                        return
                    }
                }
                println("Override file $blobFile")
                blobFile.write().use {
                    req.input.copyTo(it, pool)
                }
                resp.status = 204
                return
            }
        }
        if (req.method == "POST" && blobPath == "/uploads/") {
            println("Reg Image for upload: ${req.getAsCurl()}")
            val uuid = Random.uuid()
            blobDir.relative(uuid.toString()).write().use {
                req.input.copyTo(it, pool)
            }

            patchs[uuid] = image
            resp.status = 202
            resp.resetHeader("Server", "Nexus/3.22.0-02 (OSS)")
            resp.resetHeader(
                "Content-Security-Policy",
                "sandbox allow-forms allow-modals allow-popups allow-presentation allow-scripts allow-top-navigation"
            )
            resp.resetHeader("Docker-Distribution-Api-Version", "registry/2.0")

            resp.resetHeader("Docker-Upload-Uuid", uuid.toString())
            resp.resetHeader("Range", "0-0")
            resp.resetHeader("X-Content-Type-Options", "nosniff")
            resp.resetHeader("X-Xss-Protection", "1; mode=block")
            resp.resetHeader(Headers.LOCATION, "/v2/${image}/blobs/uploads/$uuid")
            resp.resetHeader(Headers.CONTENT_LENGTH, "0")
            return
        }

        if (req.method == "PUT") {
            val digest = req.parseGetParams()["digest"]!!.single()!!.removePrefix("sha256:")
            println("digest: [$digest]")
            req.headers.forEach { k ->
                k.value.forEach {
                    println("${k.key}: $it")
                }
            }
            config.root.relative(image).relative("blobs").relative(digest).write().use {
                val r = req.input.copyTo(it, pool)
                it.flush()
                r
            }
            resp.resetHeader(Headers.LOCATION, "/v2/$image/blobs/$digest")
            resp.resetHeader(Headers.CONTENT_LENGTH, "0")
            resp.resetHeader("Docker-Content-Digest", "sha256:$digest")
            resp.status = 201
            return
        }

        if (req.method == "PATCH") {
            println("image->[${image}]")
            val p = req.contextUri.lastIndexOf('/')
            if (p < 0) {
                resp.status = 403
                return
            }
            val uuid = UUID.fromString(req.contextUri.substring(p + 1))
            val imgName = patchs[uuid]
            if (imgName == null) {
                println("Patch for $uuid not found")
                resp.status = 404
                return
            }
            val sha256 = Sha256MessageDigest()
            val tmpFile = config.root.relative(imgName).relative("blobs").relative(uuid.toString())
            try {
                println("Writing ${tmpFile.name}")
                tmpFile.write().use {
                    req.input.copyToAndCalc(it, pool, sha256)
                    it.flush()
                }
                println("Wrote! ${tmpFile.name}")
                val hexName = sha256.finish().toHex()
                resp.resetHeader("Docker-Distribution-Api-Version", "registry/2.0")
                resp.resetHeader("Alt-Svc", "h3-29=\":443\"; ma=2592000")
                resp.resetHeader("docker-content-digest", "sha256:$hexName")
                resp.resetHeader("Docker-Upload-Uuid", uuid.toString())
                resp.resetHeader("Date", Date(Date.now).calendar(0).toString())
                resp.resetHeader("Server", "Nexus/3.22.0-02 (OSS)")
                resp.resetHeader("X-Content-Type-Options", "nosniff")
                resp.resetHeader(
                    "Content-Security-Policy",
                    "sandbox allow-forms allow-modals allow-popups allow-presentation allow-scripts allow-top-navigation"
                )
                resp.resetHeader("X-Xss-Protection", "1; mode=block")
                resp.resetHeader("Location", "/v2/${image}/blobs/uploads/$uuid")

//                resp.resetHeader(
//                    "content-security-policy",
//                    "sandbox allow-forms allow-modals allow-popups allow-presentation allow-scripts allow-top-navigation"
//                )
                val finalFile = config.root.relative(imgName).relative("blobs").relative(hexName)
                resp.resetHeader("Range", "0-${finalFile.size.toULong()}")
                if (finalFile.isExist) {
                    println("File exist! Replace!")
                    tmpFile.delete()
                } else {
                    val ok = tmpFile.renameTo(finalFile)
                    println("File created ${tmpFile.name} -> ${finalFile.name} = $ok")
                }

                println("Patch for $uuid ${req.getAsCurl()}")
                resp.resetHeader(Headers.CONTENT_LENGTH, "0")

                resp.status = 202
                resp.complete()
            } catch (e: Throwable) {
                println("Error! remove $tmpFile")
                e.printStackTrace()
                if (tmpFile.isExist) {
                    tmpFile.delete()
                    resp.status = 400
                }
            }
            return
        }
        println("404: ${req.getAsCurl()}")
        resp.status = 404
        resp.complete()
    }

    override suspend fun request(req: HttpRequest, resp: HttpResponse) {
        val (allowAccess, user) = checkUser(req, resp)
        if (!allowAccess) {
            return
        }
        if (req.contextUri == "/") {
            resp.status = 200
            resp.complete()
            println("all is ok!")
            return
        }

        blobProcessing(user, req, resp)
    }

    private fun blobPatch(req: HttpRequest, resp: HttpResponse) {

    }
}

fun ByteArray.toHex() =
    joinToString("") {
        val str = it.toUByte().toString(16)
        if (str.length == 1) {
            "0$str"
        } else {
            str
        }
    }

fun HttpRequest.getAsCurl(): String {
    val sb = StringBuilder("curl -X ${method} https://images.binom.pw${uri} -H 'Host:images.binom.pw'")
    headers.forEach { item ->
        when (item.key) {
            "X-Forwarded-Proto", "Host", "Accept-Encoding", "X-Forwarded-For" -> return@forEach
        }
        item.value.forEach {
            sb.append(" -H '${item.key}: $it'")
        }
    }
    return sb.toString()
}

private fun generateError(code: String, message: String) = Json.encodeToString(
    DockerResponse.serializer(),
    DockerResponse(
        listOf(
            ErrorDescription(
                code,
                message,
                null
            )
        )
    )
)

fun generateUnauthorized(msg: String = "access to the requested resource is not authorized") =
    generateError("UNAUTHORIZED", msg)

suspend fun AsyncInput.copyToAndCalc(output: Output, pool: ObjectPool<ByteBuffer>, messageDigest: MessageDigest): Long {
    var totalLength = 0L
    val buffer = pool.borrow()
    try {
        while (true) {
            buffer.clear()
            val length = read(buffer)
            if (length == 0) {
                break
            }
            totalLength += length.toLong()
            buffer.flip()
            val limit = buffer.limit
            output.write(buffer)
            buffer.position = 0
            buffer.limit = limit
            messageDigest.update(buffer)
        }
    } finally {
        pool.recycle(buffer)
    }
    return totalLength
}

fun HttpRequest.visitGetParams(func: (key: String, value: String?) -> Boolean) {
    val p = contextUri.lastIndexOf('?')
    if (p == -1) {
        return
    }

    contextUri.substring(p + 1).split('&').forEach {
        val items = it.split('=', limit = 2)
        if (items.size == 1) {
            func(urlDecode(items[0]), null)
        } else {
            func(urlDecode(items[0]), urlDecode(items[1]))
        }
    }
}

fun HttpRequest.parseGetParams(): Map<String, List<String?>> {
    val out = HashMap<String, ArrayList<String?>>()
    visitGetParams { key, value ->
        out.getOrPut(key) { ArrayList() }.add(value)
        true
    }
    return out
}