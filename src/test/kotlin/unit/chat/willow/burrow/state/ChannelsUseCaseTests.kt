package unit.chat.willow.burrow.state

import chat.willow.burrow.state.*
import chat.willow.burrow.utility.makeClient
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.irc.CharacterCodes
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PartMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.prefix.prefix
import io.reactivex.observers.TestObserver
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ChannelsUseCaseTests {

    private lateinit var sut: ChannelsUseCase

    private lateinit var mockClients: MockClientsUseCase

    private lateinit var sends: TestObserver<Pair<ClientTracker.ConnectedClient, Any>>

    @Before fun setUp() {
        mockClients = MockClientsUseCase()

        sends = mockClients.sendSubject.test()

        sut = ChannelsUseCase(mockClients)
    }

    @Test fun `when a client sends a JOIN message, with a valid channel name, reply with a JOIN`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        sends.assertValueAt(0, testClient.client to JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere")))
    }

    @Test fun `when a client sends a JOIN message, with an invalid channel name, reply with NOSUCHCHANNEL`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere!")))

        val message = KaleNumerics.NOSUCHCHANNEL.Message(source = "bunnies.", target = "someone", channel = "#somewhere!", content = "No such channel")
        sends.assertValue(testClient.client to message)
    }

    @Test fun `when a client joins a channel, and the channel was empty, send a NAMREPLY with only them`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        val names = listOf("someone")
        val message = Rpl353Message.Message(source = "bunnies.", target = "someone", visibility = CharacterCodes.EQUALS.toString(), channel = "#somewhere", names = names)
        sends.assertValueAt(1, testClient.client to message)
    }

    @Test fun `when a client joins a channel, and the channel was empty, send a NAMREPLY with current users and an ENDOFNAMES`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        mockClients.stubLookUpClients = mapOf(
                "someone" to testClientOne.client,
                "someone_else" to testClientTwo.client
        )
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        val names = listOf("someone", "someone_else")
        val namReplyMessage = Rpl353Message.Message(source = "bunnies.", target = "someone_else", visibility = CharacterCodes.EQUALS.toString(), channel = "#somewhere", names = names)
        val endOfNamesMessage = KaleNumerics.ENDOFNAMES.Message(source = "bunnies.", target = "someone_else", channel = "#somewhere", content = "End of /NAMES list")

        sends.assertValueAt(4, (testClientTwo.client to namReplyMessage))
        sends.assertValueAt(5, (testClientTwo.client to endOfNamesMessage))
    }

    @Test fun `when a client joins a channel, and the channel wasn't empty, send a JOIN to all the other clients`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else"))
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        mockClients.stubLookUpClients = mapOf(
                "someone" to testClientOne.client,
                "someone_else" to testClientTwo.client
        )
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)

        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        sends.assertValueAt(0, (testClientOne.client to JoinMessage.Message(source = prefix("someone"), channels = listOf("#somewhere"))))
        sends.assertValueAt(3, (testClientTwo.client to JoinMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere"))))
        sends.assertValueAt(6, (testClientOne.client to JoinMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere"))))
    }

    @Test fun `when a client joins multiple channels, each gets a JOIN`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        sut.track(testClient.client)

        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        sends.assertValueAt(0, testClient.client to JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere")))
        sends.assertValueAt(3, testClient.client to JoinMessage.Message(source = testClient.prefix, channels = listOf("#somewhere_else")))
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

        sends = mockClients.sendSubject.test()
        clientOneParts.onNext(PartMessage.Command(channels = listOf("#existing_channel")))

        sends.assertValue(testClientOne.client to PartMessage.Message(source = testClientOne.prefix, channels = listOf("#existing_channel")))
    }

    @Test fun `when a client parts multiple channels, each gets a PART`() {
        val testClient = makeClient()
        val joins = testClient.mock(JoinMessage.Command.Descriptor)
        val parts = testClient.mock(PartMessage.Command.Descriptor)
        sut.track(testClient.client)
        joins.onNext(JoinMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        sends = mockClients.sendSubject.test()
        parts.onNext(PartMessage.Command(channels = listOf("#somewhere", "#somewhere_else")))

        sends.assertValues(
                (testClient.client to PartMessage.Message(source = testClient.prefix, channels = listOf("#somewhere"))),
                (testClient.client to PartMessage.Message(source = testClient.prefix, channels = listOf("#somewhere_else")))
        )
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
        mockClients.stubLookUpClients = mapOf(
                "someone" to testClientOne.client,
                "someone_else" to testClientTwo.client
        )
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        val partSends = mockClients.sendSubject.test()
        clientOneParts.onNext(PartMessage.Command(channels = listOf("#somewhere")))
        clientTwoParts.onNext(PartMessage.Command(channels = listOf("#somewhere")))

        partSends.assertValues(
                (testClientOne.client to PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere"))),
                (testClientTwo.client to PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere"))),
                (testClientTwo.client to PartMessage.Message(source = prefix("someone_else"), channels = listOf("#somewhere")))
        )
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

        sends = mockClients.sendSubject.test()
        mockClients.droppedSubject.onNext(testClientOne.client)

        sends.assertEmpty()
    }

    @Test fun `when there's multiple users in a channel, and one of them drops, send PARTs to all the other users`() {
        val testClientOne = makeClient(id = 1, prefix = Prefix(nick = "someone"))
        val testClientTwo = makeClient(id = 2, prefix = Prefix(nick = "someone_else_1"))
        val testClientThree = makeClient(id = 3, prefix = Prefix(nick = "someone_else_2"))
        val clientOneJoins = testClientOne.mock(JoinMessage.Command.Descriptor)
        val clientTwoJoins = testClientTwo.mock(JoinMessage.Command.Descriptor)
        val clientThreeJoins = testClientThree.mock(JoinMessage.Command.Descriptor)
        mockClients.stubLookUpClients = mapOf(
                "someone" to testClientOne.client,
                "someone_else_1" to testClientTwo.client,
                "someone_else_2" to testClientThree.client
        )
        sut.track(testClientOne.client)
        sut.track(testClientTwo.client)
        sut.track(testClientThree.client)
        clientOneJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientTwoJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))
        clientThreeJoins.onNext(JoinMessage.Command(channels = listOf("#somewhere")))

        sends = mockClients.sendSubject.test()
        mockClients.droppedSubject.onNext(testClientOne.client)

        sends.assertValues(
                (testClientTwo.client to PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere"))),
                (testClientThree.client to PartMessage.Message(source = prefix("someone"), channels = listOf("#somewhere")))
        )
    }

}