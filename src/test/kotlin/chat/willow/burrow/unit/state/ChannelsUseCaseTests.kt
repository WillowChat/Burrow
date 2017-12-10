package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.ChannelsUseCase
import chat.willow.burrow.utility.KaleUtilities
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.subjects.PublishSubject
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChannelsUseCaseTests {

    private lateinit var sut: ChannelsUseCase

    private lateinit var mockConnections: IConnectionTracker
    private lateinit var mockKale: IKale

    private lateinit var joins: PublishSubject<JoinMessage.Command>

    @Before fun setUp() {
        mockConnections = mock()
        mockKale = KaleUtilities.mockKale()

        joins = mockKaleObservable(mockKale, JoinMessage.Command.Descriptor)

        sut = ChannelsUseCase(mockConnections)
    }

    @Test fun `when a client sends a JOIN message, reply with a JOIN`() {
        val client = makeClient(mockKale)
        sut.track(client)

        joins.onNext(JoinMessage.Command(channels = listOf("somewhere")))

        verify(mockConnections).send(id = 1, message = JoinMessage.Message(source = client.prefix, channels = listOf("somewhere")))
    }

    @Test fun `when a client joins a nonexistent channel, it is created`() {
        val client = makeClient(mockKale)
        sut.track(client)

        joins.onNext(JoinMessage.Command(channels = listOf("new_channel")))

        assertTrue(sut.channels.contains("new_channel"))
    }

    @Test fun `when a client joins the same channel, with different cases, only one channel is made`() {
        val client = makeClient(mockKale)
        sut.track(client)

        joins.onNext(JoinMessage.Command(channels = listOf("new_channel", "New_Channel")))

        assertTrue(sut.channels.contains("new_channel"))
        assertTrue(sut.channels.contains("New_Channel"))
        assertEquals(1, sut.channels.all.keys.count())
    }

}