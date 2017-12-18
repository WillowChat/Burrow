package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.PingUseCase
import chat.willow.burrow.utility.KaleUtilities.mockKale
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.*
import io.reactivex.schedulers.TestScheduler
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class PingUseCaseTests {

    private lateinit var sut: PingUseCase

    private lateinit var mockConnections: IConnectionTracker

    private lateinit var scheduler: TestScheduler

    @Before fun setUp() {
        mockConnections = mock()

        scheduler = TestScheduler()

        sut = PingUseCase(mockConnections, scheduler)
    }

    @Test fun `when a client is tracked, we start responding to client pings`() {
        val testClient = makeClient(prefix = prefix("someone"))
        val pings = testClient.mock(PingMessage.Command.Descriptor)

        sut.track(testClient.client)
        pings.onNext(PingMessage.Command(token = "something"))

        verify(mockConnections).send(id = 1, message = PongMessage.Message(token = "something"))
    }

    @Test fun `after 30 seconds, we send a ping command to the client`() {
        val testClient = makeClient()

        sut.track(testClient.client)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        verify(mockConnections).send(id = 1, message = PingMessage.Command(token = "bunnies"))
    }

    @Test fun `after sending a ping, wait 30 seconds for a reply, and fire a timeout`() {
        val testClient = makeClient()
        val timeouts = sut.timeout.test()
        sut.track(testClient.client)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        timeouts.assertValue(testClient.client)
    }

    @Test fun `after sending a ping, and the client pongs with a correct token within 30 seconds, no timeout is fired`() {
        val testClient = makeClient()
        val pongs = testClient.mock(PongMessage.Message.Descriptor)
        val timeouts = sut.timeout.test()
        sut.track(testClient.client)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)
        verify(mockConnections).send(id = 1, message = PingMessage.Command(token = "bunnies"))

        pongs.onNext(PongMessage.Message(token = "bunnies"))
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        timeouts.assertEmpty()
    }

    @Test fun `after sending a ping, and the client pongs with an incorrect token, fire a timeout`() {
        val testClient = makeClient()
        val pongs = testClient.mock(PongMessage.Message.Descriptor)
        val timeouts = sut.timeout.test()
        sut.track(testClient.client)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)
        verify(mockConnections).send(id = 1, message = PingMessage.Command(token = "bunnies"))

        pongs.onNext(PongMessage.Message(token = "not_bunnies"))
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        timeouts.assertValue(testClient.client)
    }

    @Test fun `after a client successfully PONGs, they're sent another PING after 30 seconds`() {
        val kale = mockKale()
        val testClient = makeClient(kale)
        val pongs = testClient.mock(PongMessage.Message.Descriptor)
        val timeouts = sut.timeout.test()
        sut.track(testClient.client)
        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)

        verify(mockConnections).send(id = 1, message = PingMessage.Command(token = "bunnies"))
        reset(mockConnections)
        pongs.onNext(PongMessage.Message(token = "bunnies"))
        scheduler.triggerActions()
        timeouts.assertEmpty()

        scheduler.advanceTimeBy(30, TimeUnit.SECONDS)
        verify(mockConnections).send(id = 1, message = PingMessage.Command(token = "bunnies"))
        reset(mockConnections)
        pongs.onNext(PongMessage.Message(token = "bunnies"))
        timeouts.assertEmpty()
    }

}