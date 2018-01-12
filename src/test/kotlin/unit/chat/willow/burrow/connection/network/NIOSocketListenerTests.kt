package unit.chat.willow.burrow.connection.network

import chat.willow.burrow.connection.IConnectionIdProvider
import chat.willow.burrow.connection.IPrimitiveConnection
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.NIOSocketListener
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import chat.willow.burrow.connection.network.INIOWrapper
import chat.willow.burrow.connection.network.ISelectionKeyWrapper
import chat.willow.burrow.helper.IInterruptedChecker
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey

class NIOSocketListenerTests {

    private lateinit var sut: NIOSocketListener
    private lateinit var mockNioWrapper: INIOWrapper
    private lateinit var incomingBuffer: ByteBuffer
    private lateinit var mockInterruptedChecker: IInterruptedChecker
    private lateinit var mockIdProvider: IConnectionIdProvider
    private lateinit var mockConnectionPreparing: IConnectionPreparing

    @Before fun setUp() {
        mockNioWrapper = mock()
        incomingBuffer = ByteBuffer.allocate(10)
        mockInterruptedChecker = mock()
        mockIdProvider = mock()
        mockConnectionPreparing = mock()

        sut = NIOSocketListener(
            "",
            0,
            mockNioWrapper,
            incomingBuffer,
            mockInterruptedChecker,
            mockIdProvider,
            mockConnectionPreparing
        )
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

        val mockSocket: IPrimitiveConnection = mock()

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

        val mockSocket: IPrimitiveConnection = mock()
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

        verify(mockNioWrapper).read(originalKey, incomingBuffer)
    }

//    @Test fun `when run, and there is a readable key, and meq 0 bytes to read, passes buffer and bytes read to delegate`() {
//        val readableKey: ISelectionKeyWrapper = mock()
//        val originalKey: SelectionKey = mock()
//        whenever(readableKey.original).thenReturn(originalKey)
//
//        whenever(readableKey.isReadable).thenReturn(true)
//
//        whenever(mockInterruptedChecker.isInterrupted)
//                .thenReturn(false)
//                .thenReturn(true)
//        whenever(mockNioWrapper.select())
//                .thenReturn(mutableSetOf(readableKey))
//
//        whenever(mockNioWrapper.read(any(), any()))
//                .thenReturn(10 to 1)
//
//        val readTest = sut.read.test()
//        sut.start()
//
//        // todo: encapsulate threading so this test can run
//
//        readTest.assertValue(IConnectionListening.Read(id = 1, bytes = byteArrayOf(0x00)))
//    }

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