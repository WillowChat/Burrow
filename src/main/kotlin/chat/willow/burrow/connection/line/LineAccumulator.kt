package chat.willow.burrow.connection.line

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.ConnectionId
import java.nio.ByteBuffer

val NEW_LINE_BYTE = '\n'.toByte()
val CARRIAGE_RETURN_BYTE = '\r'.toByte()

interface ILineAccumulator {

    fun add(bytes: ByteArray, bytesRead: Int)

}

interface ILineAccumulatorListener {

    fun onLineAccumulated(id: ConnectionId, line: String)
    fun onBufferOverran(id: ConnectionId)

}

class LineAccumulator(private val bufferSize: Int, private val connectionId: Int, private val listener: ILineAccumulatorListener): ILineAccumulator {

    private val buffer: ByteBuffer = ByteBuffer.allocate(bufferSize)

    // TODO: tidy after test cases are solidified

    override fun add(bytes: ByteArray, bytesRead: Int) {
        var startPosition = 0
        val endPosition = bytesRead

        while (startPosition < endPosition) {
            // look through from the start to see if there's a newline
            var newLinePosition = -1

            for (i in startPosition..endPosition-1) {
                if (bytes[i] == NEW_LINE_BYTE) {
                    newLinePosition = i
                    break
                }
            }

            if (newLinePosition >= startPosition) {
                // new line found, is there space for it in the incomingBuffer?
                if (buffer.position() + newLinePosition - startPosition >= bufferSize) {
                    buffer.clear()
                    listener.onBufferOverran(connectionId)
                    return
                }

                if (newLinePosition > startPosition) {
                    buffer.put(bytes, startPosition, newLinePosition - startPosition)
                }

                // remove trailing carriage return
                if (buffer.position() >= 1 && buffer[buffer.position() - 1] == CARRIAGE_RETURN_BYTE) {
                    buffer.position(buffer.position() - 1)
                }

                val line = String(bytes = buffer.array(), charset = Burrow.Server.UTF_8, offset = 0, length = buffer.position())
                buffer.clear()

                listener.onLineAccumulated(connectionId, line)

                startPosition = newLinePosition + 1

                if (startPosition >= endPosition) {
                    return
                }
            } else {
                // no newline, try to put all of the bytes on the incomingBuffer, check for size limit

                val bytesRemaining = endPosition - startPosition
                if (bytesRemaining + buffer.position() >= bufferSize) {
                    buffer.clear()
                    listener.onBufferOverran(connectionId)
                    return
                }

                buffer.put(bytes, startPosition, bytesRemaining)

                return
            }
        }

    }

}