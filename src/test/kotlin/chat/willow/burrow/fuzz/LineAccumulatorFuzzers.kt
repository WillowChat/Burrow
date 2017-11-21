package chat.willow.burrow.fuzz

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.line.LineAccumulator
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import java.util.*

class LineAccumulatorFuzzers {

    private lateinit var sut: LineAccumulator

    private val random = Random()

    @Before
    fun setUp() {
        sut = LineAccumulator(bufferSize = 16)
    }

    @Test
    fun `fuzzing add`() {
        val testObserver = sut.lines.test()

        for (i in 0 until 1_000_000) {
            val string = generateRandomLine(dictionary = listOf(" ", "a", "b", "c", "D", "E", "F", "1", "2", "3", "!", "@", "#", "🔥", "🥕", "✨"), maxByteSize = 16)
            val stringBytes = string.toByteArray(Burrow.Server.UTF_8)

            sut.input.onNext(LineAccumulator.Input(bytes = stringBytes, read = stringBytes.size))
        }

        testObserver.assertValueCount(1_000_000)
    }

    private fun generateRandomLine(dictionary: List<String>, maxByteSize: Int): String {
        val targetByteSize = random.nextInt(maxByteSize)

        val sb = StringBuilder()

        while (true) {
            val current = sb.toString()
            val currentSize = current.toByteArray(charset = Burrow.Server.UTF_8).size

            val nextThing = dictionary[random.nextInt(dictionary.size)]
            val nextThingSize = nextThing.toByteArray(charset = Burrow.Server.UTF_8).size

            if (nextThingSize + currentSize > (targetByteSize - 2)) {
                return sb.toString() + "\r\n"
            } else {
                sb.append(nextThing)
            }
        }
    }

}