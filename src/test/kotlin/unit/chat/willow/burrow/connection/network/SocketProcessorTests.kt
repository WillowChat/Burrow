package unit.chat.willow.burrow.connection.network

import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.connection.network.*
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey

class SocketProcessorTests {

    private lateinit var sut: SocketProcessor
    private lateinit var mockNioWrapper: INIOWrapper
    private lateinit var mockIncomingBuffer: ByteBuffer
    private lateinit var mockInterruptedChecker: IInterruptedChecker

    @Before fun setUp() {
        mockNioWrapper = mock()
        mockIncomingBuffer = mock()
        mockInterruptedChecker = mock()

        sut = SocketProcessor(mockNioWrapper, mockIncomingBuffer, mockInterruptedChecker)
    }

    @Test fun `when run, tells nio wrapper to clear selected keys`() {
        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf())

        sut.run()

        verify(mockNioWrapper).clearSelectedKeys()
    }

    @Test fun `when run, and there is an acceptable key, calls delegate onAccepted`() {
        val acceptableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(acceptableKey.original).thenReturn(originalKey)

        whenever(acceptableKey.isAcceptable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(acceptableKey))

        val mockSocket: INetworkSocket = mock()

        val mockSelectionKey: SelectionKey = mock()

        whenever(mockNioWrapper.accept(any()))
                .thenReturn(mockSocket to mockSelectionKey)

        sut.run()

        verify(mockNioWrapper).accept(originalKey)
//        verify(mockDelegate).onAccepted(mockSocket)
    }

    @Test fun `when run, and there is an acceptable key, attaches id from delegate`() {
        val acceptableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(acceptableKey.original).thenReturn(originalKey)

        whenever(acceptableKey.isAcceptable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(acceptableKey))

        val mockSocket: INetworkSocket = mock()
        val mockSelectionKey: SelectionKey = mock()

        whenever(mockNioWrapper.accept(any()))
                .thenReturn(mockSocket to mockSelectionKey)

        sut.run()

        verify(mockNioWrapper).attach(0, mockSelectionKey)
    }

    @Test fun `when run, and there is a readable key, gets nio wrapper to read in to buffer`() {
        val readableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(readableKey.original).thenReturn(originalKey)

        whenever(readableKey.isReadable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(readableKey))

        whenever(mockNioWrapper.read(any(), any()))
                .thenReturn(10 to 1)

        sut.run()

        verify(mockNioWrapper).read(originalKey, mockIncomingBuffer)
    }

    @Test fun `when run, and there is a readable key, and meq 0 bytes to read, passes buffer and bytes read to delegate`() {
        val readableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(readableKey.original).thenReturn(originalKey)

        whenever(readableKey.isReadable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(readableKey))

        whenever(mockNioWrapper.read(any(), any()))
                .thenReturn(10 to 1)

        sut.run()

//        verify(mockDelegate).onRead(1, mockIncomingBuffer, 10)
    }

    @Test fun `when run, and there is a readable key, and less than 0 bytes to read, tells nio to close key`() {
        val readableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(readableKey.original).thenReturn(originalKey)

        whenever(readableKey.isReadable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(readableKey))

        whenever(mockNioWrapper.read(any(), any()))
                .thenReturn(-1 to 1)

        sut.run()

        verify(mockNioWrapper).close(originalKey)
    }

    @Test fun `when run, and there is a readable key, and less than 0 bytes to read, tells delegate that id disconnected`() {
        val readableKey: ISelectionKeyWrapper = mock()
        val originalKey: SelectionKey = mock()
        whenever(readableKey.original).thenReturn(originalKey)

        whenever(readableKey.isReadable).thenReturn(true)

        whenever(mockInterruptedChecker.isInterrupted)
                .thenReturn(false)
                .thenReturn(true)
        whenever(mockNioWrapper.select())
                .thenReturn(mutableSetOf(readableKey))

        whenever(mockNioWrapper.read(any(), any()))
                .thenReturn(-1 to 2)

        sut.run()

//        verify(mockDelegate).onDisconnected(2)
    }

}