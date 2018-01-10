package unit.chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.*
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.preparing.HaproxyConnectionPreparing
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.IHaproxyHeaderDecoder
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import unit.chat.willow.burrow.connection.line.MockLineAccumulator
import java.net.InetAddress
import java.nio.ByteBuffer

class HaproxyConnectionPreparingTests {

    lateinit var sut: HaproxyConnectionPreparing

    lateinit var mockFactory: IBurrowConnectionFactory
    lateinit var mockDecoder: IHaproxyHeaderDecoder

    @Before fun setUp() {
        mockFactory = mock()
        mockDecoder = mock()

        sut = HaproxyConnectionPreparing(mockFactory, mockDecoder)
    }

    @Test fun `if the first input is not decodable, connection is dropped`() {
        whenever(mockDecoder.decode(any())).then { throw RuntimeException() }

        val input = PublishSubject.create<IConnectionListening.Read>()
        val accumulator = LineAccumulator(bufferSize = 1)
        val connection = IConnectionListening.Accepted(id = 1, primitiveConnection = mock())
        val tracked = PublishSubject.create<ConnectionTracker.Tracked>().test()
        val drop = PublishSubject.create<ConnectionId>().test()
        val connections = mutableMapOf<ConnectionId, BurrowConnection>()
        sut.prepare(input, accumulator, connection, tracked, drop, connections)
        input.onNext(IConnectionListening.Read(id = 1, buffer = ByteBuffer.allocate(1), bytesRead = 1))

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
        sut.prepare(input, accumulator, connection, tracked, drop, connections)
        input.onNext(IConnectionListening.Read(id = 1, buffer = ByteBuffer.allocate(1), bytesRead = 1))

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
        sut.prepare(input, accumulator, connection, tracked, drop, connections)
        input.onNext(IConnectionListening.Read(id = 1, buffer = ByteBuffer.allocate(1), bytesRead = 1))

        accumulatorInput.assertValue(ILineAccumulator.Input(bytes = byteArrayOf(0x00, 0x01), bytesRead = 2))
    }

}

fun stubAddress(hostname: String): InetAddress {
    val stubAddress: InetAddress = mock()
    whenever(stubAddress.hostName).thenReturn(hostname)
    whenever(stubAddress.canonicalHostName).thenReturn(hostname)
    return stubAddress
}