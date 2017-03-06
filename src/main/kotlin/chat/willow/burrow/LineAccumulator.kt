package chat.willow.burrow

import java.nio.ByteBuffer

interface ILineAccumulator {

    fun add(bytes: ByteArray, bytesRead: Int)

}

interface ILineAccumulatorDelegate {

    fun onLineAccumulated(connectionId: Int, line: String)
    fun onBufferOverran(connectionId: Int)

}

class LineAccumulator(private val bufferSize: Int, private val connectionId: Int, private val delegate: ILineAccumulatorDelegate): ILineAccumulator {

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
                // new line found, is there space for it in the buffer?
                if (buffer.position() + newLinePosition - startPosition >= bufferSize) {
                    buffer.clear()
                    delegate.onBufferOverran(connectionId)
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

                delegate.onLineAccumulated(connectionId, line)

                startPosition = newLinePosition + 1

                if (startPosition >= endPosition) {
                    return
                }
            } else {
                // no newline, try to put all of the bytes on the buffer, check for size limit

                val bytesRemaining = endPosition - startPosition
                if (bytesRemaining + buffer.position() >= bufferSize) {
                    buffer.clear()
                    delegate.onBufferOverran(connectionId)
                    return
                }

                buffer.put(bytes, startPosition, bytesRemaining)

                return
            }
        }

    }

}