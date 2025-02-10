package pw.binom.io.httpClient
/*
import pw.binom.DEFAULT_BUFFER_SIZE
import pw.binom.SafeException
import pw.binom.io.AsyncOutput
import pw.binom.io.ByteBuffer
import pw.binom.io.DataTransferSize
import pw.binom.io.http.*
import pw.binom.io.httpClient.protocol.v11.Http11
import pw.binom.url.URL

class Htp11SchemaSelector(val factory: NetSocketFactory, val fallback: SchemaSelector) : SchemaSelector {
    private sealed interface ContentLength {
        data object CHUNKED : ContentLength
        data object NO_CONTENT : ContentLength
        data class Fixed(val size: ULong) : ContentLength
    }

    private object ClosedAsyncOutput : AsyncOutput {
        override suspend fun asyncClose() {
        }

        override suspend fun flush() {
            //
        }

        override suspend fun write(data: ByteBuffer): DataTransferSize =
            DataTransferSize.CLOSED

    }

    companion object {
        suspend fun http(
            url: URL,
            factory: NetSocketFactory,
            defaultPort: Int,
            method: String,
            headers: Headers,
            bufferSize: Int = DEFAULT_BUFFER_SIZE,
        ): HttpConnection {
            val channel = factory.connect(
                host = url.domain,
                port = url.port ?: defaultPort
            )
            val asciiChannel = AsyncAsciiChannel(channel = channel, bufferSize = bufferSize)
            SafeException.async {
                Http11.sendRequest(
                    output = asciiChannel.writer,
                    method = method,
                    request = url.request,
                    headers = headers,
                )
            }
            val bodyLen = headers.httpContentLength
            if (method == "HEAD" || bodyLen == HttpContentLength.NONE) {
                ClosedAsyncOutput
            } else {
                when (bodyLen) {
                    HttpContentLength.NONE -> ClosedAsyncOutput
                    HttpContentLength.CHUNKED -> {
                        AsyncChunkedOutput(
                            stream = asciiChannel.writer,
                            autoFlushBuffer = bufferSize - 2,
                            closeStream = false,
                        )
                    }

                    is HttpContentLength.Fixed -> {
                        val stream = if (bodyLen.chunked) {
                            AsyncChunkedOutput(
                                stream = asciiChannel.writer,
                                autoFlushBuffer = bufferSize - 2,
                                closeStream = false,
                            )
                        } else {
                            asciiChannel.writer
                        }
                        AsyncContentLengthOutput(
                            stream = stream,
                            contentLength = bodyLen.size,
                            closeStream = false,
                        )
                    }
                }
            }
        }
    }

    override suspend fun connect(url: URL): HttpConnection {
        val schema = url.schema
        if (schema != "http" && schema != "ws") {
            return fallback.connect(url)
        }
        return http(factory = factory, url = url)
    }
}

 */