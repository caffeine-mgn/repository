package pw.binom.repo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pw.binom.*
import pw.binom.io.*
import pw.binom.io.file.*
import pw.binom.io.httpServer.*
import pw.binom.pool.ObjectPool

@Serializable
class ErrorDescription(val code: String, val message: String, val detail: String?)

@Serializable
class DockerResponse(
    val errors: List<ErrorDescription>
)

fun ByteArray.toHex() =
    joinToString("") {
        val str = it.toUByte().toString(16)
        if (str.length == 1) {
            "0$str"
        } else {
            str
        }
    }

fun String.fromHex(): ByteArray {
    require(length % 2 == 0)
    val out = ByteArray(length / 2)
    for (i in 0 until length / 2) {
        out[i] = substring(i * 2, i * 2 + 2).toUByte(16).toByte()
    }
    return out
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