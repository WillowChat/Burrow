package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.ClientUseCase
import chat.willow.burrow.utility.KaleUtilities
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ClientUseCaseTests {

    private lateinit var sut: ClientUseCase
    private lateinit var mockConnectionTracker: IConnectionTracker
    private lateinit var mockKale: IKale

    @Before fun setUp() {
        mockConnectionTracker = mock()
        mockKale = KaleUtilities.mockKale()

        sut = ClientUseCase(connections = mockConnectionTracker)
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val client = makeClient(mockKale)

        sut.track.onNext(client)

        verify(mockConnectionTracker).send(id = 1, message = Rpl001MessageType(source = "bunnies", target = "someone", contents = "welcome to burrow"))
    }

    @Test fun `after tracking a client, we can look them up by username`() {
        val client = makeClient(mockKale, prefix = prefix(nick = "someone"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertEquals("someone", result?.name)
    }

    @Test fun `if we haven't tracked a client, we can't look them up by username`() {
        val client = makeClient(mockKale, prefix = prefix(nick = "someone else"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

    @Test fun `we can't look up a dropped client`() {
        val client = makeClient(mockKale, id = 1, prefix = prefix(nick = "someone"))
        sut.track.onNext(client)
        sut.drop.onNext(1)

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

}