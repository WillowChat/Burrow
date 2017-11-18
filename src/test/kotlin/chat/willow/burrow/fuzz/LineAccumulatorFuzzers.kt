package chat.willow.burrow.fuzz

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.line.ILineAccumulatorListener
import chat.willow.burrow.connection.line.LineAccumulator
import com.nhaarman.mockito_kotlin.mock
import org.junit.Before
import org.junit.Test
import java.util.*

class LineAccumulatorFuzzers {

    private lateinit var sut: LineAccumulator
    private lateinit var mockListener: ILineAccumulatorListener

    @Before
    fun setUp() {
        mockListener = mock()

        sut = LineAccumulator(bufferSize = 16, connectionId = 1, listener = mockListener)
    }

    @Test
    fun `fuzzing add`() {
        for (i in 0..1_000_000) {
            val string = generateRandomLine(dictionary = " abcDEF123!@#🔥🥕✨", maxLength = 16)
            val stringBytes = string.toByteArray(Burrow.Server.UTF_8)
            sut.add(stringBytes, stringBytes.size)
        }
    }

    private fun generateRandomLine(dictionary: String, maxLength: Int): String {
        val random = Random()
        val length = random.nextInt(maxLength)

        val sb = StringBuilder()

        for (i in 0..length - 1 - 2) {
            sb.append(dictionary[random.nextInt(dictionary.length)])
        }

        if (random.nextBoolean()) {
            return sb.toString() + "\r\n"
        } else {
            return sb.toString()
        }
    }

}