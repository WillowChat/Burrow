package chat.willow.burrow.unit.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.state.*
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.utility.namedMap
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.*
import org.junit.Before
import org.junit.Test

class ChannelMessagesUseCaseTests {

    lateinit var sut: ChannelMessagesUseCase

    lateinit var mockConnections: IConnectionTracker
    lateinit var mockChannels: IChannelsUseCase
    lateinit var mockClients: IClientsUseCase

    lateinit var channels: CaseInsensitiveNamedMap<Channel>

    @Before fun setUp() {
        mockConnections = mock()
        mockChannels = mock()
        mockClients = mock()

        channels = namedMap()
        whenever(mockChannels.channels).thenReturn(channels)

        sut = ChannelMessagesUseCase(mockConnections, mockChannels, mockClients)
    }

    @Test fun `when a client sends a message to a channel with an invalid name, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(false)

        privMsgs.onNext(PrivMsgMessage.Command(target = "not_valid", message = "something"))

        verify(mockConnections, only()).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "not_valid", content = "Invalid channel name"))
    }

    @Test fun `when a client sends a message to a nonexistent channel, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "something"))

        verify(mockConnections, only()).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "#somewhere", content = "Channel doesn't exist"))
    }

    @Test fun `when a client sends a message to a channel they aren't in, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)
        channels += Channel(name = "#somewhere", users = namedMap())

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "something"))

        verify(mockConnections, only()).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "#somewhere", content = "You're not in that channel"))
    }

    @Test fun `when a client sends an invalid message to a channel, send an error back`() {
        val testClientOne = makeClient(prefix = prefix("someone"))
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)
        channels += Channel(name = "#somewhere", users = namedMap(listOf(ChannelUser(prefix = prefix("someone")))))

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = ""))

        verify(mockConnections, only()).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "#somewhere", content = "That message was invalid"))
    }

    @Test fun `when a client sends an valid message to a valid channel, send the message to other clients in the channel`() {
        val testClientOne = makeClient(id = 1, prefix = prefix("someone"))
        val testClientTwo = makeClient(id = 2, prefix = prefix("someone_else"))
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)
        channels += Channel(name = "#somewhere", users = namedMap(listOf(
                ChannelUser(prefix = prefix("someone")),
                ChannelUser(prefix = prefix("someone_else"))
        )))
        whenever(mockClients.lookUpClient("someone")).thenReturn(testClientOne.client)
        whenever(mockClients.lookUpClient("someone_else")).thenReturn(testClientTwo.client)

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "🐰"))

        verify(mockConnections, never()).send(id = eq(1), message = any<PrivMsgMessage.Message>())
        verify(mockConnections).send(id = 2, message = PrivMsgMessage.Message(source = prefix("someone"), target = "#somewhere", message = "🐰"))
    }

    @Test fun `when a client sends a valid message to a channel where they're the only participant, nothing happens`() {
        val testClientOne = makeClient(id = 1, prefix = prefix("someone"))
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)
        channels += Channel(name = "#somewhere", users = namedMap(listOf(
                ChannelUser(prefix = prefix("someone"))
        )))
        whenever(mockClients.lookUpClient("someone")).thenReturn(testClientOne.client)

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "🥕"))

        verifyZeroInteractions(mockConnections)
    }

}