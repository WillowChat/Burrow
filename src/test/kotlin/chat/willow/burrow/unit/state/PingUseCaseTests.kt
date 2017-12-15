package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.PingUseCase
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test

class PingUseCaseTests {

    private lateinit var sut: PingUseCase

    private lateinit var mockConnections: IConnectionTracker

    @Before fun setUp() {
        mockConnections = mock()

        sut = PingUseCase(mockConnections)
    }

    @Test
    fun `when a client is tracked, we start responding to client pings`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val pings = testClient.mock(PingMessage.Command.Descriptor)

        sut.track(testClient.client)
        pings.onNext(PingMessage.Command(token = "something"))

        verify(mockConnections).send(id = 1, message = PongMessage.Message(token = "something"))
    }

}