package unit.chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.state.*
import chat.willow.kale.IKale
import chat.willow.kale.core.message.IrcMessage
import chat.willow.kale.core.message.KaleDescriptor
import chat.willow.kale.core.message.KaleObservable
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt

class ClientTrackerTests {

    private lateinit var sut: ClientTracker
    private lateinit var mockConnectionTracker: IConnectionTracker
    private lateinit var mockRegistration: IRegistrationUseCase
    private lateinit var mockClientsUseCase: IClientsUseCase
    private lateinit var mockKaleFactory: IKaleFactory
    private lateinit var mockKale: IKale

    private var lines = PublishSubject.create<String>()
    private var messages = PublishSubject.create<KaleObservable<IrcMessage>>()
    private var read = PublishSubject.create<String>()
    private var reads = mutableMapOf(1 to read)
    private var dropped = PublishSubject.create<ConnectionTracker.Dropped>()

    private lateinit var track: PublishSubject<RegistrationUseCase.Registered>

    @Before fun setUp() {
        mockConnectionTracker = mock()
        whenever(mockConnectionTracker.lineReads).thenReturn(reads)
        whenever(mockConnectionTracker.dropped).thenReturn(dropped)

        mockRegistration = mock()
        mockClientsUseCase = mock()
        mockKaleFactory = mock()
        mockKale = mock()

        whenever(mockKaleFactory.create()).thenReturn(mockKale)
        whenever(mockKale.lines).thenReturn(lines)
        whenever(mockKale.messages).thenReturn(messages)

        whenever(mockClientsUseCase.track).thenReturn(PublishSubject.create())
        whenever(mockClientsUseCase.drop).thenReturn(PublishSubject.create())

        val kaleObservable = PublishSubject.create<KaleObservable<*>>()
        whenever(mockKale.observe(any<KaleDescriptor<*>>())).thenReturn(kaleObservable)

        track = PublishSubject.create<RegistrationUseCase.Registered>()
        whenever(mockRegistration.track(any(), any(), any())).thenReturn(track)

        sut = ClientTracker(mockConnectionTracker, mockRegistration, mockClientsUseCase, mockKaleFactory, supportedCaps = mapOf("something" to null))
    }

    @Test fun `when a client is tracked, we track them with the registration use case`() {
        val connection = BurrowConnection(id = 1, primitiveConnection = mock())

        sut.track.onNext(connection)

        verify(mockRegistration).track(mockKale, mapOf("something" to null), connection)
    }

    @Test fun `when a client fails to register, they're dropped`() {
        val connection = BurrowConnection(id = 1, primitiveConnection = mock())
        sut.track.onNext(connection)
        val drop = PublishSubject.create<ConnectionId>()
        whenever(mockConnectionTracker.drop).thenReturn(drop)

        val observer = drop.test()
        track.onError(RuntimeException("intentional failure"))

        observer.assertValue(1)
    }

}