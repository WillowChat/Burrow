package chat.willow.burrow.connection

import chat.willow.burrow.ILineAccumulator
import chat.willow.burrow.ILineAccumulatorListener
import chat.willow.burrow.ILineAccumulatorPool
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.network.INetworkSocket
import chat.willow.kale.IKale
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConnectionTrackerTests {

    private lateinit var sut: ConnectionTracker
    private lateinit var mockLineAccumulatorPool: ILineAccumulatorPool
    private lateinit var mockLineAccumulator: ILineAccumulator
    private lateinit var mockKale: IKale

    @Before fun setUp() {
        mockLineAccumulatorPool = mock()
        mockLineAccumulator = mock()
        mockKale = mock()

        whenever(mockLineAccumulatorPool.next(any(), any())).thenReturn(mockLineAccumulator)

        sut = ConnectionTracker(lineAccumulatorPool = mockLineAccumulatorPool)
    }

    @Test fun `track returns a new valid connection`() {
        val socket: INetworkSocket = mock()
        val listener: ILineAccumulatorListener = mock()

        val connection = sut.track(socket, listener)

        assertTrue(sut[connection.id] === connection)
    }

    @Test fun `track uses the line accumulator pool`() {
        val socket: INetworkSocket = mock()
        val listener: ILineAccumulatorListener = mock()

        val connection = sut.track(socket, listener)

        verify(mockLineAccumulatorPool).next(connection.id, listener)
    }

    @Test fun `track assigns new connections different ids`() {
        val socket: INetworkSocket = mock()
        val listener: ILineAccumulatorListener = mock()

        val connectionOne = sut.track(socket, listener)
        val connectionTwo = sut.track(socket, listener)

        assertNotEquals(connectionOne.id, connectionTwo.id)
    }

    @Test fun `drop results in same connection lookup failing`() {
        val socket: INetworkSocket = mock()
        val listener: ILineAccumulatorListener = mock()

        val connection = sut.track(socket, listener)
        sut -= connection.id

        val connectionLookup = sut[connection.id]

        assertNull(connectionLookup)
    }

}