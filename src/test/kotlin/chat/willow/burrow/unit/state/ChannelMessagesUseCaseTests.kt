package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.state.ChannelMessagesUseCase
import chat.willow.burrow.state.IChannelsUseCase
import chat.willow.burrow.state.Rpl404MessageType
import chat.willow.burrow.utility.makeClient
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test

class ChannelMessagesUseCaseTests {

    lateinit var sut: ChannelMessagesUseCase

    lateinit var mockConnections: IConnectionTracker
    lateinit var mockChannels: IChannelsUseCase

    @Before fun setUp() {
        mockConnections = mock()
        mockChannels = mock()

        sut = ChannelMessagesUseCase(mockConnections, mockChannels)
    }

    @Test fun `when a client sends a message to a channel with an invalid name, send an error back`() {
        val testClientOne = makeClient()
        val privMsgs = testClientOne.mock(PrivMsgMessage.Command.Descriptor)
        sut.track(testClientOne.client)

        privMsgs.onNext(PrivMsgMessage.Command(target = "not_valid", message = "something"))

        verify(mockConnections).send(id = 1, message = Rpl404MessageType(source = "bunnies", target = "someone", channel = "not_valid", content = "Invalid channel name"))
    }

    // todo: CannotSendToChan 404

}