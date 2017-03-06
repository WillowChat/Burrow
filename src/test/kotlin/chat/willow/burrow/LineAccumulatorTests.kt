package chat.willow.burrow

import chat.willow.burrow.Burrow.Server.Companion.UTF_8
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import org.junit.Before
import org.junit.Test
import java.util.*

class LineAccumulatorTests {

    private lateinit var sut: LineAccumulator
    private lateinit var mockDelegate: ILineAccumulatorDelegate

    @Before fun setUp() {
        mockDelegate = mock()

        sut = LineAccumulator(bufferSize = 16, connectionId = 1, delegate = mockDelegate)
    }

    @Test fun `when a single line that fits exactly in to the default buffer is added, onLineAccumulated is called with correct parameters`() {
        val testString = "12345678123456\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verify(mockDelegate).onLineAccumulated(1, "12345678123456")
    }

    @Test fun `when two lines that fit in to a single buffer are added, onLineAccumulated is called twice with correct parameters`() {
        val testString = "1234\r\n12345678\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        inOrder(mockDelegate) {
            verify(mockDelegate).onLineAccumulated(1, "1234")
            verify(mockDelegate).onLineAccumulated(1, "12345678")
        }
    }

    @Test fun `when two lines that overlap buffers are added, onLineAccumulated is called twice with correct parameters`() {
        val testString = "123456781234\r\n1234567812345\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        inOrder(mockDelegate) {
            verify(mockDelegate).onLineAccumulated(1, "123456781234")
            verify(mockDelegate).onLineAccumulated(1, "1234567812345")
        }
    }

    @Test fun `when a line and remaining bytes are added, then a newline is sent, onLineAccumulated is called twice with correct parameters`() {
        val testString = "123456781234\r\n1234567812345"
        val testStringAsBytes = testString.toByteArray(UTF_8)
        val testNewline = "\r\n"
        val testNewlineAsBytes = testNewline.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)
        sut.add(testNewlineAsBytes, testNewlineAsBytes.size)

        inOrder(mockDelegate) {
            verify(mockDelegate).onLineAccumulated(1, "123456781234")
            verify(mockDelegate).onLineAccumulated(1, "1234567812345")
        }
    }

    @Test fun `when a single line that does not in the buffer is added, onBufferOverran is called with correct parameters`() {
        val testString = "1234567812345678\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verify(mockDelegate).onBufferOverran(1)
    }

    @Test fun `when two lines are added, and the second overruns, delegate is called twice as expected`() {
        val testString = "12345678123\r\n123456789123456789"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        inOrder(mockDelegate) {
            verify(mockDelegate).onLineAccumulated(1, "12345678123")
            verify(mockDelegate).onBufferOverran(1)
        }
    }

    @Test fun `empty string does not result in any delegate calls`() {
        val testString = ""
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verifyZeroInteractions(mockDelegate)
    }

    @Test fun `single character does not result in any delegate calls`() {
        val testString = "a"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verifyZeroInteractions(mockDelegate)
    }

    @Test fun `empty line results in onLineAccumulated with an empty string`() {
        val testString = "\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verify(mockDelegate).onLineAccumulated(1, "")
    }

    @Test fun `single carriage return does not result in any delegate calls`() {
        val testString = "\r"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verifyZeroInteractions(mockDelegate)
    }

    @Test fun `single carriage return and then a single newline results in onLineAccumulated with empty string`() {
        val testStringOne = "\r"
        val testStringTwo = "\n"
        val testStringOneAsBytes = testStringOne.toByteArray(UTF_8)
        val testStringTwoAsBytes = testStringTwo.toByteArray(UTF_8)

        sut.add(testStringOneAsBytes, testStringOneAsBytes.size)
        sut.add(testStringTwoAsBytes, testStringTwoAsBytes.size)

        verify(mockDelegate).onLineAccumulated(1, "")
    }

    @Test fun `lines with emoji are processed`() {
        val testString = "123456🥕✨\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        sut.add(testStringAsBytes, testStringAsBytes.size)

        verify(mockDelegate).onLineAccumulated(1, "123456🥕✨")
    }

    // TODO: move to `slow test` runner
    @Test fun `fuzzing for crashes`() {
        for (i in 0..1_000_000) {
            val string = generateRandomLine(dictionary = " abcDEF123!@#🔥🥕✨", maxLength = 16)
            val stringBytes = string.toByteArray(UTF_8)
            sut.add(stringBytes, stringBytes.size)
        }
    }

    fun generateRandomLine(dictionary: String, maxLength: Int): String {
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