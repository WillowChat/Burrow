package chat.willow.burrow

import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.kale.irc.message.IrcMessage
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test

class IrcMessageProcessorTests {

    private lateinit var sut: IrcMessageProcessor
    private lateinit var mockInterruptedChecker: IInterruptedChecker

    @Before fun setUp() {
        mockInterruptedChecker = mock()

        sut = IrcMessageProcessor(mockInterruptedChecker)
    }

    @Test fun `when run is called, it checks if interrupted`() {
        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(true)

        sut.run()

        verify(mockInterruptedChecker).isInterrupted
    }

    @Test fun `when an item is added with plusAssign and then run, there are no exceptions`() {
        val clientOne = BurrowClient(1, mock(), mock())
        val messageOne = IrcMessage(command = "1")

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)

        sut += clientOne to messageOne

        sut.run()
    }

}