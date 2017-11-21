package chat.willow.burrow.unit

import chat.willow.burrow.connection.line.LineProcessor
import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.kale.IBurrowKaleWrapper
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test

class LineProcessorTests {

    private lateinit var sut: LineProcessor
    private lateinit var mockInterruptedChecker: IInterruptedChecker
    private lateinit var mockBurrowKaleWrapper: IBurrowKaleWrapper

    @Before fun setUp() {
        mockInterruptedChecker = mock()
        mockBurrowKaleWrapper = mock()

        sut = LineProcessor(mockInterruptedChecker, mockBurrowKaleWrapper)
    }

    @Test fun `when run is called, it checks if interrupted`() {
        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(true)

        sut.run()

        verify(mockInterruptedChecker).isInterrupted
    }

    @Test fun `when an item is added with plusAssign and then run, there are no exceptions`() {
        val clientOne = BurrowConnection(1, "host", mock(), mock())
        val messageOne = "1"

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)

        sut += clientOne to messageOne

        sut.run()
    }

}