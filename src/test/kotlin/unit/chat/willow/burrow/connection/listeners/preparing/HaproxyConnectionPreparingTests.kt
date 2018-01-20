package unit.chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.*
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.preparing.HaproxyConnectionPreparing
import chat.willow.burrow.connection.listeners.preparing.IHostLookupUseCase
import chat.willow.burrow.connection.listeners.preparing.PlainConnectionPreparing
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.IHaproxyHeaderDecoder
import chat.willow.kale.core.message.IrcMessage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import unit.chat.willow.burrow.connection.line.MockLineAccumulator
import java.net.InetAddress

class HaproxyConnectionPreparingTests {

    lateinit var sut: HaproxyConnectionPreparing

    lateinit var mockFactory: IBurrowConnectionFactory
    lateinit var mockDecoder: IHaproxyHeaderDecoder
    lateinit var mockHostnameLookup: IHostLookupUseCase
    lateinit var lookupScheduler: TestScheduler

    @Before fun setUp() {
        mockFactory = mock()
        mockDecoder = mock()
        mockHostnameLookup = mock()
        whenever(mockHostnameLookup.lookUp(any(), any())).thenReturn(Observable.just("somewhere"))

        lookupScheduler = TestScheduler()

        sut = HaproxyConnectionPreparing(mockFactory, mockDecoder, mockHostnameLookup, lookupScheduler)
    }

    @Test fun `if the first input is not decodable, connection is dropped`() {
        whenever(mockDecoder.decode(any())).then { throw RuntimeException() }

        val input = PublishSubject.create<IConnectionListening.Read>()
        val accumulator = LineAccumulator(bufferSize = 1)
        val connection = IConnectionListening.Accepted(id = 1, primitiveConnection = mock())
        val tracked = PublishSubject.create<ConnectionTracker.Tracked>().test()
        val drop = PublishSubject.create<ConnectionId>().test()
        val connections = mutableMapOf<ConnectionId, BurrowConnection>()
        val send = PublishSubject.create<IrcMessage>().test()
        sut.prepare(input, accumulator, connection, tracked, drop, connections, send)
        input.onNext(IConnectionListening.Read(id = 1, bytes = byteArrayOf(0x00)))

        tracked.assertEmpty()
        drop.assertValues(1)
    }

    @Test fun `if the first input is decodable, with no extra input, connection is added and tracked`() {
        val header = HaproxyHeaderDecoder.Header(stubAddress("source"), 1, stubAddress("destination"), 2)
        val decodeOutput = HaproxyHeaderDecoder.Output(header, remainingBytes = byteArrayOf(), remainingBytesRead = 0)
        whenever(mockDecoder.decode(any())).thenReturn(decodeOutput)

        val primitive: IPrimitiveConnection = mock()
        val burrowConnection = BurrowConnection(id = 1, primitiveConnection = primitive)
        whenever(mockFactory.create(any(), any())).thenReturn(burrowConnection)
        val input = PublishSubject.create<IConnectionListening.Read>()
        val accumulator = MockLineAccumulator()
        val accumulatorInput = accumulator.inputSubject.test()
        val connection = IConnectionListening.Accepted(id = 1, primitiveConnection = primitive)
        val tracked = PublishSubject.create<ConnectionTracker.Tracked>().test()
        val drop = PublishSubject.create<ConnectionId>().test()
        val connections = mutableMapOf<ConnectionId, BurrowConnection>()
        val send = PublishSubject.create<IrcMessage>().test()
        sut.prepare(input, accumulator, connection, tracked, drop, connections, send)
        input.onNext(IConnectionListening.Read(id = 1, bytes = byteArrayOf(0x00)))
        lookupScheduler.triggerActions()

        drop.assertEmpty()
        tracked.assertValue(ConnectionTracker.Tracked(burrowConnection))
        accumulatorInput.assertNoValues()
    }

    @Test fun `if the first input is decodable, with extra input, input is replayed`() {
        val header = HaproxyHeaderDecoder.Header(stubAddress("source"), 1, stubAddress("destination"), 2)
        val decodeOutput = HaproxyHeaderDecoder.Output(header, remainingBytes = byteArrayOf(0x00, 0x01), remainingBytesRead = 2)
        whenever(mockDecoder.decode(any())).thenReturn(decodeOutput)

        val primitive: IPrimitiveConnection = mock()
        val burrowConnection = BurrowConnection(id = 1, primitiveConnection = primitive)
        whenever(mockFactory.create(any(), any())).thenReturn(burrowConnection)
        val input = PublishSubject.create<IConnectionListening.Read>()
        val accumulator = MockLineAccumulator()
        val accumulatorInput = accumulator.inputSubject.test()
        val connection = IConnectionListening.Accepted(id = 1, primitiveConnection = primitive)
        val tracked = PublishSubject.create<ConnectionTracker.Tracked>().test()
        val drop = PublishSubject.create<ConnectionId>().test()
        val connections = mutableMapOf<ConnectionId, BurrowConnection>()
        val send = PublishSubject.create<IrcMessage>().test()
        sut.prepare(input, accumulator, connection, tracked, drop, connections, send)
        input.onNext(IConnectionListening.Read(id = 1, bytes = byteArrayOf(0x00)))
        lookupScheduler.triggerActions()

        accumulatorInput.assertValue(ILineAccumulator.Input(bytes = byteArrayOf(0x00, 0x01), bytesRead = 2))
    }

    @Test fun `looking up hostname message is sent after haproxy frame`() {
        val header = HaproxyHeaderDecoder.Header(stubAddress("source"), 1, stubAddress("destination"), 2)
        val decodeOutput = HaproxyHeaderDecoder.Output(header, remainingBytes = byteArrayOf(0x00, 0x01), remainingBytesRead = 2)
        whenever(mockDecoder.decode(any())).thenReturn(decodeOutput)

        val primitive: IPrimitiveConnection = mock()
        val burrowConnection = BurrowConnection(id = 1, primitiveConnection = primitive)
        whenever(mockFactory.create(any(), any())).thenReturn(burrowConnection)
        val input = PublishSubject.create<IConnectionListening.Read>()
        val accumulator = MockLineAccumulator()
        val connection = IConnectionListening.Accepted(id = 1, primitiveConnection = primitive)
        val tracked = PublishSubject.create<ConnectionTracker.Tracked>().test()
        val drop = PublishSubject.create<ConnectionId>().test()
        val connections = mutableMapOf<ConnectionId, BurrowConnection>()
        val send = PublishSubject.create<IrcMessage>().test()
        sut.prepare(input, accumulator, connection, tracked, drop, connections, send)
        input.onNext(IConnectionListening.Read(id = 1, bytes = byteArrayOf(0x00)))
        lookupScheduler.triggerActions()

        send.assertValue(PlainConnectionPreparing.LOOKING_UP_MESSAGE)
    }

}

fun stubAddress(hostname: String): InetAddress {
    val stubAddress: InetAddress = mock()
    whenever(stubAddress.hostName).thenReturn(hostname)
    whenever(stubAddress.hostAddress).thenReturn(hostname)
    whenever(stubAddress.canonicalHostName).thenReturn(hostname)
    return stubAddress
}