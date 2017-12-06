package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.ClientUseCase
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test

class ClientUseCaseTests {

    private lateinit var sut: ClientUseCase
    private lateinit var mockConnectionTracker: IConnectionTracker
    private lateinit var mockKale: IKale

    @Before fun setUp() {
        mockConnectionTracker = mock()
        mockKale = mock()

        sut = ClientUseCase(connections = mockConnectionTracker)
    }

    @Test fun `when a client is tracked, we start responding to client pings`() {
        val pingMessages = mockKaleObservable(mockKale, PingMessage.Command.Descriptor)
        val accumulator = LineAccumulator(bufferSize = 1)
        val connection = BurrowConnection(id = 1, host = "", socket = mock(), accumulator = accumulator)

        val client = ClientTracker.ConnectedClient(connection, mockKale, prefix("someone"))
        sut.track(client)
        pingMessages.onNext(PingMessage.Command(token = "something"))

        verify(mockConnectionTracker).send(id = 1, message = PongMessage.Message(token = "something"))
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        mockKaleObservable(mockKale, PingMessage.Command.Descriptor)
        val accumulator = LineAccumulator(bufferSize = 1)
        val connection = BurrowConnection(id = 1, host = "", socket = mock(), accumulator = accumulator)
        val client = ClientTracker.ConnectedClient(connection, mockKale, prefix("someone"))

        sut.track(client)

        verify(mockConnectionTracker).send(id = 1, message = Rpl001MessageType(source = "bunnies", target = "someone", contents = "welcome to burrow"))
    }

}