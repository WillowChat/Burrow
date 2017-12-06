package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.ClientUseCase
import chat.willow.kale.IKale
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test

class ClientUseCaseTests {

    private lateinit var sut: ClientUseCase
    private lateinit var mockConnectionTracker: IConnectionTracker
    private lateinit var mockKale: IKale

    private lateinit var pings: PublishSubject<PingMessage.Command>
    private lateinit var joins: PublishSubject<JoinMessage.Command>

    @Before fun setUp() {
        mockConnectionTracker = mock()
        mockKale = mock()

        pings = mockKaleObservable(mockKale, PingMessage.Command.Descriptor)
        joins = mockKaleObservable(mockKale, JoinMessage.Command.Descriptor)

        sut = ClientUseCase(connections = mockConnectionTracker)
    }

    @Test fun `when a client is tracked, we start responding to client pings`() {
        val client = makeClient(mockKale)

        sut.track(client)
        pings.onNext(PingMessage.Command(token = "something"))

        verify(mockConnectionTracker).send(id = 1, message = PongMessage.Message(token = "something"))
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val client = makeClient(mockKale)

        sut.track(client)

        verify(mockConnectionTracker).send(id = 1, message = Rpl001MessageType(source = "bunnies", target = "someone", contents = "welcome to burrow"))
    }

    @Test fun `when a client sends a JOIN message, reply with a JOIN`() {
        val client = makeClient(mockKale)
        sut.track(client)

        joins.onNext(JoinMessage.Command(channels = listOf("somewhere")))

        verify(mockConnectionTracker).send(id = 1, message = JoinMessage.Message(source = client.prefix, channels = listOf("somewhere")))
    }

    private fun makeClient(kale: IKale, id: ConnectionId = 1, prefix: Prefix = prefix("someone")): ClientTracker.ConnectedClient {
        val accumulator = LineAccumulator(bufferSize = 1)
        val connection = BurrowConnection(id = id, host = prefix.host ?: "", socket = mock(), accumulator = accumulator)

        return ClientTracker.ConnectedClient(connection, mockKale, prefix("someone"))
    }

}