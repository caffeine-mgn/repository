package pw.binom.io.httpClient

import pw.binom.SafeException
import pw.binom.date.DateTime
import pw.binom.io.*
import pw.binom.io.http.*
import pw.binom.io.httpClient.protocol.v11.Http11
import pw.binom.logger.Logger
import pw.binom.logger.info

/**
 * Держатель HTTP подключения для HTTP-1.1
 */
class Http11ConnectionImpl(
    private val channel: AsyncAsciiChannel,
    private val autoFlushSize: Int,
    private val pushBack: suspend (Http11ConnectionImpl) -> Unit
) : HttpConnection {
    companion object;
    private val logger by Logger.ofThisOrGlobal

    private object EmptyAsyncOutput : AsyncOutput {
        override suspend fun asyncClose() {
        }

        override suspend fun flush() {
            //
        }

        override suspend fun write(data: ByteBuffer): DataTransferSize = DataTransferSize.EMPTY
    }

    override var state: HttpConnection.State = HttpConnection.State.READY
        private set
    override var lastActive = DateTime.now
        private set

    override suspend fun connect(
        method: String,
        request: String,
        headers: Headers,
    ): Http11ClientExchange = when (state) {
        HttpConnection.State.BUSY -> throw IllegalStateException("Connection is busy")
        HttpConnection.State.CLOSED -> throw IllegalStateException("Connection is closed")
        HttpConnection.State.READY -> SafeException.async {
            val keepAlive = headers.keepAlive == true
            if (!keepAlive) {
                state = HttpConnection.State.CLOSED
            }
            logger.info("Sending http request. keepAlive=$keepAlive")
            Http11.sendRequest(
                output = channel.writer,
                method = method,
                request = request,
                headers = headers,
            )
            channel.writer.flush()
            val bodyLen = headers.httpContentLength
            logger.info("bodyLen=$bodyLen")
            val outputStream = if (method == "HEAD" || bodyLen == HttpContentLength.NONE) {
                EmptyAsyncOutput
            } else {
                when (bodyLen) {
                    HttpContentLength.NONE -> EmptyAsyncOutput
                    HttpContentLength.CHUNKED -> {
                        AsyncChunkedOutput(
                            stream = channel.writer,
                            autoFlushBuffer = autoFlushSize,
                            closeStream = false,
                        ).closeOnException()
                    }

                    is HttpContentLength.Fixed -> {
                        val stream = when {
                            bodyLen.chunked -> AsyncChunkedOutput(
                                stream = channel.writer,
                                autoFlushBuffer = autoFlushSize,
                                closeStream = false,
                            )

                            else -> channel.writer
                        }
                        AsyncContentLengthOutput(
                            stream = stream,
                            contentLength = bodyLen.size,
                            closeStream = false,
                        )
                    }
                }
            }
            state = HttpConnection.State.BUSY
            lastActive = DateTime.now
            logger.info("Return exchange!")
            Http11ClientExchange(
                input = channel.reader, output = outputStream, onClose = { isError ->
                    when {
                        state == HttpConnection.State.CLOSED -> channel.asyncCloseAnyway()
                        keepAlive && !isError -> when {
                            outputStream === EmptyAsyncOutput -> state = HttpConnection.State.READY
                            (outputStream as? AsyncContentLengthOutput)?.isFull == true -> {
                                state = HttpConnection.State.READY
                                pushBack(this@Http11ConnectionImpl)
                            }

                            outputStream is AsyncChunkedOutput -> {
                                outputStream.asyncClose()
                                state = HttpConnection.State.READY
                                pushBack(this@Http11ConnectionImpl)
                            }

                            else -> {
                                state = HttpConnection.State.CLOSED
                                channel.asyncCloseAnyway()
                            }
                        }

                        else -> {
                            state = HttpConnection.State.CLOSED
                            channel.asyncCloseAnyway()
                        }
                    }
                })
        }
    }

    override suspend fun asyncClose() {
        channel.asyncClose()
    }
}