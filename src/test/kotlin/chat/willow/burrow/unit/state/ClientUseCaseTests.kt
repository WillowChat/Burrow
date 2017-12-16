package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.ClientsUseCase
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ClientUseCaseTests {

    private lateinit var sut: ClientsUseCase
    private lateinit var mockConnectionTracker: IConnectionTracker

    @Before fun setUp() {
        mockConnectionTracker = mock()

        sut = ClientsUseCase(connections = mockConnectionTracker)
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val (_, client) = makeClient()

        sut.track.onNext(client)

        verify(mockConnectionTracker).send(id = 1, message = Rpl001Message.Message(source = "bunnies", target = "someone", content = "welcome to burrow"))
    }

    @Test fun `after tracking a client, we can look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertEquals("someone", result?.name)
    }

    @Test fun `if we haven't tracked a client, we can't look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone else"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

    @Test fun `we can't look up a dropped client`() {
        val (_, client) = makeClient(id = 1, prefix = prefix("someone"))
        sut.track.onNext(client)
        sut.drop.onNext(1)

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

}