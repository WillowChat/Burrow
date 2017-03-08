package chat.willow.burrow

import chat.willow.burrow.helper.INIOSocketChannelWrapper
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.channels.SocketChannel

class ClientTrackerTests {

    private lateinit var sut: ClientTracker
    private lateinit var mockLineAccumulatorPool: ILineAccumulatorPool
    private lateinit var mockLineAccumulator: ILineAccumulator

    @Before fun setUp() {
        mockLineAccumulatorPool = mock()
        mockLineAccumulator = mock()

        whenever(mockLineAccumulatorPool.next(any(), any())).thenReturn(mockLineAccumulator)

        sut = ClientTracker(lineAccumulatorPool = mockLineAccumulatorPool)
    }

    @Test fun `track returns a new valid client`() {
        val socket: INIOSocketChannelWrapper = mock()
        val listener: ILineAccumulatorListener = mock()

        val client = sut.track(socket, listener)

        assertTrue(sut[client.id] === client)
    }

    @Test fun `track uses the line accumulator pool`() {
        val socket: INIOSocketChannelWrapper = mock()
        val listener: ILineAccumulatorListener = mock()

        val client = sut.track(socket, listener)

        verify(mockLineAccumulatorPool).next(client.id, listener)
    }

    @Test fun `track assigns new clients different ids`() {
        val socket: INIOSocketChannelWrapper = mock()
        val listener: ILineAccumulatorListener = mock()

        val clientOne = sut.track(socket, listener)
        val clientTwo = sut.track(socket, listener)

        assertNotEquals(clientOne.id, clientTwo.id)
    }

    @Test fun `drop results in same client lookup failing`() {
        val socket: INIOSocketChannelWrapper = mock()
        val listener: ILineAccumulatorListener = mock()

        val client = sut.track(socket, listener)
        sut -= client.id

        val clientLookup = sut[client.id]

        assertNull(clientLookup)
    }

}