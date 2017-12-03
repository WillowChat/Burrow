package chat.willow.burrow.unit.connection.network

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.connection.network.INetworkSocket
import chat.willow.burrow.connection.network.ISocketProcessor
import chat.willow.burrow.connection.network.SocketProcessor
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.message.IrcMessageSerialiser
import chat.willow.kale.irc.message.utility.RawMessage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.sql.Connection

class ConnectionTrackerTests {

    lateinit var sut: ConnectionTracker

    lateinit var mockSocketProcessor: ISocketProcessor
    lateinit var mockKale: IKale
    lateinit var mockConnectionFactory: IBurrowConnectionFactory

    lateinit var accepted: PublishSubject<SocketProcessor.Accepted>
    lateinit var read: PublishSubject<SocketProcessor.Read>
    lateinit var closed: PublishSubject<SocketProcessor.Closed>

    @Before fun setUp() {
        mockSocketProcessor = mock()
        mockKale = mock()
        mockConnectionFactory = mock()

        accepted = PublishSubject.create<SocketProcessor.Accepted>()
        whenever(mockSocketProcessor.accepted).thenReturn(accepted)

        read = PublishSubject.create<SocketProcessor.Read>()
        whenever(mockSocketProcessor.read).thenReturn(read)

        closed = PublishSubject.create<SocketProcessor.Closed>()
        whenever(mockSocketProcessor.closed).thenReturn(closed)

        sut = ConnectionTracker(mockSocketProcessor, bufferSize = 10, kale = mockKale, connectionFactory = mockConnectionFactory)
    }

    @Test fun `when socket processor accepts a socket, tracker tracks connection`() {
        val socket: INetworkSocket = mock()
        whenever(socket.host).thenReturn("somewhere")

        val accumulator: ILineAccumulator = mock()
        val connection = BurrowConnection(id = 1, host = "somewhere", socket = socket, accumulator = accumulator)
        whenever(mockConnectionFactory.create(any(), any(), any(), any())).thenReturn(connection)

        val observer = sut.tracked.test()
        accepted.onNext(SocketProcessor.Accepted(1, socket))

        val expected = ConnectionTracker.Tracked(connection)
        observer.assertValue(expected)
    }

    @Test fun `after tracking a connection, it is gettable`() {
        val socket: INetworkSocket = mock()
        whenever(socket.host).thenReturn("somewhere")

        val accumulator: ILineAccumulator = mock()
        val connection = BurrowConnection(id = 1, host = "somewhere", socket = socket, accumulator = accumulator)
        whenever(mockConnectionFactory.create(any(), any(), any(), any())).thenReturn(connection)
        accepted.onNext(SocketProcessor.Accepted(1, socket))

        assertEquals(connection, sut.get(id = 1))
    }

    @Test fun `after tracking a connection, and sending something to it, the socket is written to`() {
        val socket = mock<INetworkSocket>()
        whenever(socket.host).thenReturn("somewhere")

        val accumulator: ILineAccumulator = mock()
        val connection = BurrowConnection(id = 1, host = "somewhere", socket = socket, accumulator = accumulator)
        whenever(mockConnectionFactory.create(any(), any(), any(), any())).thenReturn(connection)
        accepted.onNext(SocketProcessor.Accepted(1, socket))

        val line = "SOME message"
        whenever(mockKale.serialise(line)).thenReturn(IrcMessage(command = "SOME", parameters = listOf("message")))
        sut.send(id = 1,  message = line)

        val expectedSocketWrite = Burrow.Server.UTF_8.encode("SOME :message\r\n")
        verify(socket).write(expectedSocketWrite)
    }

    @Test fun `when socket processor reads, the connection's accumulator is given the data`() {
        val socket: INetworkSocket = mock()
        whenever(socket.host).thenReturn("somewhere")

        val accumulator = LineAccumulator(bufferSize = 10)
        val connection = BurrowConnection(id = 1, host = "", socket = socket, accumulator = accumulator)
        whenever(mockConnectionFactory.create(any(), any(), any(), any())).thenReturn(connection)
        accepted.onNext(SocketProcessor.Accepted(id = 1, socket = socket))

        val observer = accumulator.input.test()
        val buffer = ByteBuffer.allocate(10)
        read.onNext(SocketProcessor.Read(id = 1, buffer = buffer, bytes = 8))

        observer.assertValue(LineAccumulator.Input(buffer.array(), read = 8))
    }

    @Test fun `when socket processor closes a socket, we always say that we dropped it`() {
        val observer = sut.dropped.test()

        closed.onNext(SocketProcessor.Closed(id = 1))

        observer.assertValue(ConnectionTracker.Dropped(id = 1))
    }

}