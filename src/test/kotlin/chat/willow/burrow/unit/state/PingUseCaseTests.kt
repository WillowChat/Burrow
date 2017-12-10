package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.PingUseCase
import chat.willow.burrow.utility.KaleUtilities
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test

class PingUseCaseTests {

    private lateinit var sut: PingUseCase

    private lateinit var mockConnections: IConnectionTracker
    private lateinit var mockKale: IKale

    private lateinit var pings: PublishSubject<PingMessage.Command>

    @Before fun setUp() {
        mockConnections = mock()
        mockKale = KaleUtilities.mockKale()

        pings = mockKaleObservable(mockKale, PingMessage.Command.Descriptor)

        sut = PingUseCase(mockConnections)
    }

    @Test
    fun `when a client is tracked, we start responding to client pings`() {
        val client = makeClient(mockKale)

        sut.track(client)
        pings.onNext(PingMessage.Command(token = "something"))

        verify(mockConnections).send(id = 1, message = PongMessage.Message(token = "something"))
    }

}