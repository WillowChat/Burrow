package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.state.*
import chat.willow.burrow.utility.makeClient
import chat.willow.kale.irc.CharacterCodes
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PartMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.*
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelsUseCaseTests {

    private lateinit var sut: ChannelsUseCase

    private lateinit var mockConnections: IConnectionTracker
    private lateinit var mockClients: IClientsUseCase

    private lateinit var droppeds: PublishSubject<ClientTracker.ConnectedClient>

    @Before fun setUp() {
        mockConnections = mock()
        mockClients = mock()

        droppeds = PublishSubject.create()
        whenever(mockClients.dropped).thenReturn(droppeds)

        sut = ChannelsUseCase(mockConnections, mockClients)
    }

    @Test fun `when a client sends a JOIN message, with a valid channel name, reply with a JOIN`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere")))
    }

    @Test fun `when a client sends a JOIN message, with an invalid channel name, reply with NOSUCHCHANNEL`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere!")))

        val message = Rpl403Message.Message(source = "bunnies.", target = "someone", channel = "#somewhere!", content = "No such channel")
        verify(mockConnections).send(id = 1, message = message)
    }

    @Test fun `when a client joins a channel, and the channel was empty, send a NAMREPLY with only them`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        val names = listOf("someone")
        val message = Rpl353Message.Message(source = "bunnies.", target = "someone", visibility = CharacterCodes.EQUALS.toString(), channel = "#somewhere", names = names)
        verify(mockConnections).send(id = 1, message = message)
    }

    @Test fun `when a client joins a channel, and the channel was empty, send a NAMREPLY with current users and an ENDOFNAMES`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)

        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        val names = listOf("someone", "someone_else")
        val namReplyMessage = Rpl353Message.Message(source = "bunnies.", target = "someone_else", visibility = CharacterCodes.EQUALS.toString(), channel = "#somewhere", names = names)
        val endOfNamesMessage = Rpl366Message.Message(source = "bunnies.", target = "someone_else", channel = "#somewhere", content = "End of /NAMES list")

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 2, message = namReplyMessage)
            verify(mockConnections).send(id = 2, message = endOfNamesMessage)
        }
    }

    @Test fun `when a client joins a channel, and the channel wasn't empty, send a JOIN to all the other clients`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        whenever(mockClients.lookUpClient("someone")).thenReturn(testClientOne.client)
        whenever(mockClients.lookUpClient("someone_else")).thenReturn(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 2, message = JoinMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere")))
        }
    }

    @Test fun `when a client joins multiple channels, each gets a JOIN`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere_else")))
        }
    }

    @Test fun `when a client joins a nonexistent channel, it is created`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#new_channel")))

        assertTrue(sut.channels.contains("#new_channel"))
    }

    @Test fun `when a client joins the same channel, with different cases, only one channel is made`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#new_channel", "#New_Channel")))

        assertTrue(sut.channels.contains("#new_channel"))
        assertTrue(sut.channels.contains("#New_Channel"))
        assertEquals(1, sut.channels.all.keys.count())
    }

    @Test fun `when a client leaves a channel, and it is then empty, it is destroyed`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        val parts = testClient.mock(PartMessage.Command.Descriptor)
        sut.track(testClient.client)
        joins.onNext(JoinMessage.Command(channels = listOf("#existing_channel")))

        parts.onNext(PartMessage.Command(channels = listOf("#existing_channel")))

        assertFalse(sut.channels.contains("#existing_channel"))
    }

    @Test fun `when a client leaves a channel, and it still has users, it is not destroyed`() {
        val testClientOne = makeClient(id = 1, prefix = prefix("someone"))
        val testClientTwo = makeClient(id = 2, prefix = prefix("someone_else"))
        val clientOneParts = testClientOne.mock(PartMessage.Command.Descriptor)
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#existing_channel")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#existing_channel")))

        clientOneParts.onNext(PartMessage.Command(channels = listOf("#existing_channel")))

        assertTrue(sut.channels.contains("#existing_channel"))
    }

    @Test fun `when a client leaves a channel, they get a PART reply`() {
        val testClientOne = makeClient(id = 1, prefix = prefix("someone"))
        val testClientTwo = makeClient(id = 2, prefix = prefix("someone_else"))
        val clientOneParts = testClientOne.mock(PartMessage.Command.Descriptor)
        val clientTwoJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#existing_channel")))

        clientOneParts.onNext(PartMessage.Command(channels = listOf("#existing_channel")))

        verify(mockConnections).send(id = 1, message = PartMessage.Message(source = testClientOne.prefix, channels = listOf("#existing_channel")))
    }

    @Test fun `when a client parts multiple channels, each gets a PART`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        val parts = testClient.mock(PartMessage.Command.Descriptor)
        sut.track(testClient.client)
        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        parts.onNext(PartMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 1, message = PartMessage.Message(source = testClient.prefix, channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 1, message = PartMessage.Message(source = testClient.prefix, channels = listOf("#somewhere_else")))
        }
    }

    @Test fun `when a client JOINs a channel, the channel contains them`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        assertTrue(sut.channels["#somewhere"]?.users?.contains("someone") ?: false)
    }

    @Test fun `when a client parts a channel, and the channel wasn't empty, send a PART to all the other clients`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val clientOneParts = testClientOne.mock(PartMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        val clientTwoParts = testClientTwo.mock(PartMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        whenever(mockClients.lookUpClient("someone")).thenReturn(testClientOne.client)
        whenever(mockClients.lookUpClient("someone_else")).thenReturn(testClientTwo.client)
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        clientOneParts.onNext(PartMessage.Command(channels = listOf("#somewhere")))
        clientTwoParts.onNext(PartMessage.Command(channels = listOf("#somewhere")))

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 1, message = PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 2, message = PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 2, message = PartMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere")))
        }
    }

    @Test fun `when a two clients JOIN a channel, the channel contains both of them`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        assertEquals(setOf("someone", "someone_else"), sut.channels["#somewhere"]?.users?.all?.keys)
    }

    @Test fun `when the same client joins a channel twice, there's only one of them in the channel`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        assertEquals(setOf("someone"), sut.channels["#somewhere"]?.users?.all?.keys)
    }

    @Test fun `when there's one person in a channel, and they get dropped, don't send anything`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        droppeds.onNext(testClientOne.client)

        verify(mockConnections, never()).send(any(), any<PartMessage.Message>())
    }

    @Test fun `when there's multiple users in a channel, and one of them drops, send PARTs to all the other users`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else_1"))
        val testClientThree = makeClient(id = 3, prefix = Prefix(nick = "someone_else_2"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        val clientThreeJoins = testClientThree.mock(JoinMessage.Command.Descriptor)
        whenever(mockClients.lookUpClient("someone")).thenReturn(testClientOne.client)
        whenever(mockClients.lookUpClient("someone_else_1")).thenReturn(testClientTwo.client)
        whenever(mockClients.lookUpClient("someone_else_2")).thenReturn(testClientThree.client)
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        sut.track(testClientThree.client)
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientThreeJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        droppeds.onNext(testClientOne.client)

        inOrder(mockConnections) {
            verify(mockConnections).send(id = 2, message = PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
            verify(mockConnections).send(id = 3, message = PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
        }
    }

}