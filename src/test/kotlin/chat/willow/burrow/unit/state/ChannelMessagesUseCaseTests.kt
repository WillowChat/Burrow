package chat.willow.burrow.unit.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.state.Channel
import chat.willow.burrow.state.ChannelMessagesUseCase
import chat.willow.burrow.state.IChannelsUseCase
import chat.willow.burrow.state.Rpl404MessageType
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.utility.namedMap
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test

class ChannelMessagesUseCaseTests {

    lateinit var sut: ChannelMessagesUseCase

    lateinit var mockConnections: IConnectionTracker
    lateinit var mockChannels: IChannelsUseCase

    lateinit var channels: CaseInsensitiveNamedMap<Channel>

    @Before fun setUp() {
        mockConnections = mock()
        mockChannels = mock()

        channels = namedMap()
        whenever(mockChannels.channels).thenReturn(channels)

        sut = ChannelMessagesUseCase(mockConnections, mockChannels)
    }

    @Test fun `when a client sends a message to a channel with an invalid name, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(false)

        privMsgs.onNext(PrivMsgMessage.Command(target = "not_valid", message = "something"))

        verify(mockConnections).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "not_valid", content = "Invalid channel name"))
    }

    @Test fun `when a client sends a message to a nonexistent channel, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "something"))

        verify(mockConnections).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "#somewhere", content = "Channel doesn't exist"))
    }

    @Test fun `when a client sends a message to a channel they aren't in, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)
        whenever(mockChannels.isNameValid(any(), any())).thenReturn(true)
        channels += Channel(name = "#somewhere", users = namedMap())

        privMsgs.onNext(PrivMsgMessage.Command(target = "#somewhere", message = "something"))

        verify(mockConnections).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "#somewhere", content = "You're not in that channel"))
    }

}