package unit.chat.willow.burrow.connection

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.*
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.kale.IKale
import chat.willow.kale.core.message.IrcMessage
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import unit.chat.willow.burrow.connection.listeners.MockConnectionListening
import unit.chat.willow.burrow.connection.listeners.preparing.MockConnectionPreparing

class ConnectionTrackerTests {

    lateinit var sut: ConnectionTracker

    lateinit var mockConnectionListening: IConnectionListening
    lateinit var mockKale: IKale

    lateinit var preparer: MockConnectionPreparing
    lateinit var listener: MockConnectionListening

    lateinit var scheduler: TestScheduler

    @Before fun setUp() {
        mockConnectionListening = mock()
        mockKale = mock()

        scheduler = TestScheduler()

        preparer = MockConnectionPreparing()
        listener = MockConnectionListening(preparer)

        sut = ConnectionTracker(mockKale, scheduler)
        sut.addConnectionListener(listener)
    }

    @Test fun `after tracking a connection, it is gettable`() {
        val socket: IPrimitiveConnection = mock()
        whenever(socket.host).thenReturn("somewhere")

        val connection = BurrowConnection(id = 1, primitiveConnection = socket)

        listener.acceptedSubject.onNext(IConnectionListening.Accepted(1, socket))
        scheduler.triggerActions()
        preparer.spyConnections[1] = connection

        assertEquals(connection, sut.get(id = 1))
    }

    @Test fun `after tracking a connection, and sending something to it, the socket is written to`() {
        val socket = mock<IPrimitiveConnection>()
        whenever(socket.host).thenReturn("somewhere")
        val connection = BurrowConnection(id = 1, primitiveConnection = socket)

        listener.acceptedSubject.onNext(IConnectionListening.Accepted(1, socket))
        scheduler.triggerActions()
        preparer.spyConnections[1] = connection

        val line = "SOME message"
        whenever(mockKale.serialise(line)).thenReturn(IrcMessage(command = "SOME", parameters = listOf("message")))
        sut.send.onNext(1 to line)
        scheduler.triggerActions()

        val expectedSocketWrite = Burrow.Server.UTF_8.encode("SOME :message\r\n")
        verify(socket).write(expectedSocketWrite)
    }

    @Test fun `when socket processor closes a socket, we always say that we dropped it`() {
        val observer = sut.dropped.test()

        listener.closedSubject.onNext(IConnectionListening.Closed(id = 1))
        scheduler.triggerActions()

        observer.assertValue(ConnectionTracker.Dropped(id = 1))
    }

    @Test fun `after dropping a connection, it is not gettable`() {
        val socket: IPrimitiveConnection = mock()
        whenever(socket.host).thenReturn("somewhere")
        val connection = BurrowConnection(id = 1, primitiveConnection = socket)
        listener.acceptedSubject.onNext(IConnectionListening.Accepted(1, socket))
        scheduler.triggerActions()
        preparer.spyConnections[1] = connection

        sut.drop.onNext(1)
        scheduler.triggerActions()

        assertNull(sut[1])
    }

}

class MockConnectionTracker: IConnectionTracker {

    override val lineReads: Map<ConnectionId, Observable<String>>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    var didGet = false
    var spyConnectionId: ConnectionId? = null
    var stubConnection: BurrowConnection? = null
    override fun get(id: ConnectionId): BurrowConnection? {
        didGet = true
        spyConnectionId = id
        return stubConnection
    }

    override val tracked: Observable<ConnectionTracker.Tracked>
    val trackedSubject = PublishSubject.create<ConnectionTracker.Tracked>()

    override val dropped: Observable<ConnectionTracker.Dropped>
    val droppedSubject = PublishSubject.create<ConnectionTracker.Dropped>()

    override val drop: Observer<ConnectionId>
    val dropSubject = PublishSubject.create<ConnectionId>()

    override val send: Observer<Pair<ConnectionId, Any>>
    val sendSubject = PublishSubject.create<Pair<ConnectionId, Any>>()

    init {
        tracked = trackedSubject
        dropped = droppedSubject
        drop = dropSubject
        send = sendSubject
    }

    var didAddConnectionListener = false
    lateinit var spyListener: IConnectionListening
    override fun addConnectionListener(listener: IConnectionListening) {
        didAddConnectionListener = true
        spyListener = listener
    }
}