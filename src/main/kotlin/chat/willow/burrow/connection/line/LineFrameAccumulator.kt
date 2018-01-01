package chat.willow.burrow.connection.line

import chat.willow.burrow.Burrow
import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.toObservable
import io.reactivex.subjects.PublishSubject
import java.nio.ByteBuffer

val NEW_LINE_BYTE = '\n'.toByte()
val CARRIAGE_RETURN_BYTE = '\r'.toByte()

interface ILineAccumulator {
    val input: Observer<Input>
    val lines: Observable<String>

    data class Input(val bytes: ByteArray, val bytesRead: Int)
}

class LineAccumulator(private val bufferSize: Int): ILineAccumulator {

    private val LOGGER = loggerFor<LineAccumulator>()

    private val buffer: ByteBuffer = ByteBuffer.allocate(bufferSize)

    override val lines = PublishSubject.create<String>()
    override val input = PublishSubject.create<ILineAccumulator.Input>()

    object OverranException : Exception()

    init {
        input
            .flatMap(this::accumulate)
            .subscribe(lines)
    }

    private fun accumulate(input: ILineAccumulator.Input): Observable<String> {
        val bytes = input.bytes
        val endPosition = input.bytesRead

        var startPosition = 0

        if (bytes.isEmpty()) {
            return Observable.empty()
        }

        val lines = mutableListOf<String>()

        loop@while (startPosition < endPosition) {
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
                    return Observable.merge(lines.toObservable(), Observable.error(OverranException))
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

                lines.add(line)

                startPosition = newLinePosition + 1

                if (startPosition >= endPosition) {
                    break@loop
                }
            } else {
                // no newline, try to put all of the bytesRead on the incomingBuffer, check for size limit

                val bytesRemaining = endPosition - startPosition
                if (bytesRemaining + buffer.position() >= bufferSize) {
                    buffer.clear()
                    return Observable.merge(lines.toObservable(), Observable.error(OverranException))
                }

                buffer.put(bytes, startPosition, bytesRemaining)

                break@loop
            }
        }

        return if (lines.isEmpty()) {
            Observable.empty()
        } else {
            lines.toObservable()
        }
    }

}