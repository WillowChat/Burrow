package chat.willow.burrow.unit.connection.line

import chat.willow.burrow.Burrow.Server.Companion.UTF_8
import chat.willow.burrow.connection.line.LineAccumulator
import org.junit.Before
import org.junit.Test

class LineAccumulatorTests {

    private lateinit var sut: LineAccumulator

    @Before fun setUp() {
        sut = LineAccumulator(bufferSize = 16)
    }

    @Test fun `when a single line that fits exactly in to the default buffer is given, a single line outputted`() {
        val testString = "12345678123456\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValue("12345678123456")
    }

    @Test fun `when two lines that fit in to a single buffer are added, onLineAccumulated is called twice with correct parameters`() {
        val testString = "1234\r\n12345678\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValues("1234", "12345678")
    }

    @Test fun `when two lines that overlap buffers are added, onLineAccumulated is called twice with correct parameters`() {
        val testString = "123456781234\r\n1234567812345\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValues("123456781234", "1234567812345")
    }

    @Test fun `when a line and remaining bytes are added, then a newline is sent, onLineAccumulated is called twice with correct parameters`() {
        val testString = "123456781234\r\n1234567812345"
        val testStringAsBytes = testString.toByteArray(UTF_8)
        val testNewline = "\r\n"
        val testNewlineAsBytes = testNewline.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))
        sut.input.onNext(LineAccumulator.Input(bytes = testNewlineAsBytes, read = testNewlineAsBytes.size))

        observer.assertValues("123456781234", "1234567812345")
    }

    @Test fun `when a single line that does not in the buffer is added, onBufferOverran is called with correct parameters`() {
        val testString = "1234567812345678\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertError(LineAccumulator.OverranException)
    }

    @Test fun `when two lines are added, and the second overruns, delegate is called twice as expected`() {
        val testString = "12345678123\r\n123456789123456789"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValue("12345678123").assertError(LineAccumulator.OverranException)
    }

    @Test fun `empty string does not result in any delegate calls`() {
        val testString = ""
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertNoValues()
    }

    @Test fun `single character does not result in any delegate calls`() {
        val testString = "a"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertNoValues()
    }

    @Test fun `empty line results in onLineAccumulated with an empty string`() {
        val testString = "\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValue("")
    }

    @Test fun `single carriage return does not result in any delegate calls`() {
        val testString = "\r"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertNoValues()
    }

    @Test fun `single carriage return and then a single newline results in onLineAccumulated with empty string`() {
        val testStringOne = "\r"
        val testStringTwo = "\n"
        val testStringOneAsBytes = testStringOne.toByteArray(UTF_8)
        val testStringTwoAsBytes = testStringTwo.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringOneAsBytes, read = testStringOneAsBytes.size))
        sut.input.onNext(LineAccumulator.Input(bytes = testStringTwoAsBytes, read = testStringTwoAsBytes.size))

        observer.assertValue("")
    }

    @Test fun `lines with emoji are processed`() {
        val testString = "123456🥕✨\r\n"
        val testStringAsBytes = testString.toByteArray(UTF_8)

        val observer = sut.lines.test()
        sut.input.onNext(LineAccumulator.Input(bytes = testStringAsBytes, read = testStringAsBytes.size))

        observer.assertValue("123456\uD83E\uDD55✨")
    }

}