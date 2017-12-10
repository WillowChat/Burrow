package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.ClientUseCase
import chat.willow.burrow.utility.KaleUtilities
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
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
        mockKale = KaleUtilities.mockKale()

        sut = ClientUseCase(connections = mockConnectionTracker)
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val client = makeClient(mockKale)

        sut.track(client)

        verify(mockConnectionTracker).send(id = 1, message = Rpl001MessageType(source = "bunnies", target = "someone", contents = "welcome to burrow"))
    }

}