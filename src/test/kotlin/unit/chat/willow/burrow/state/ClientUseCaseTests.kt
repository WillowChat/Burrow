package unit.chat.willow.burrow.state

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.ClientsUseCase
import chat.willow.burrow.state.IClientsUseCase
import chat.willow.burrow.utility.makeClient
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.irc.prefix.prefix
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import unit.chat.willow.burrow.configuration.networkName
import unit.chat.willow.burrow.configuration.serverName
import unit.chat.willow.burrow.connection.MockConnectionTracker

class ClientUseCaseTests {

    private lateinit var sut: ClientsUseCase
    private lateinit var mockConnections: MockConnectionTracker
    private lateinit var scheduler: TestScheduler

    private lateinit var sends: TestObserver<Pair<ConnectionId, Any>>

    @Before fun setUp() {
        scheduler = TestScheduler()

        mockConnections = MockConnectionTracker()

        sends = mockConnections.sendSubject.test()

        sut = ClientsUseCase(mockConnections, serverName(), networkName(), scheduler)
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val (_, client) = makeClient()

        sut.track.onNext(client)
        scheduler.triggerActions()

        sends.assertValue(1 to KaleNumerics.WELCOME.Message(source = serverName().name, target = "someone", content = "Welcome to Burrow"))
    }

    @Test fun `after tracking a client, we can look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone"))
        sut.track.onNext(client)
        scheduler.triggerActions()

        val result = sut.lookUpClient(nick = "someone")

        assertEquals("someone", result?.name)
    }

    @Test fun `if we haven't tracked a client, we can't look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone else"))
        sut.track.onNext(client)
        scheduler.triggerActions()

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

    @Test fun `we can't look up a dropped client`() {
        val (_, client) = makeClient(id = 1, prefix = prefix("someone"))
        sut.track.onNext(client)
        sut.drop.onNext(1)
        scheduler.triggerActions()

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

}

class MockClientsUseCase : IClientsUseCase {

    var stubLookUpClients: Map<String, ClientTracker.ConnectedClient> = mapOf()
    override fun lookUpClient(nick: String): ClientTracker.ConnectedClient? {
        return stubLookUpClients[nick]
    }

    override val track: Observer<ClientTracker.ConnectedClient>
    val trackSubject = PublishSubject.create<ClientTracker.ConnectedClient>()

    override val drop: Observer<ConnectionId>
    val dropSubject = PublishSubject.create<ConnectionId>()

    override val dropped: Observable<ClientTracker.ConnectedClient>
    val droppedSubject = PublishSubject.create<ClientTracker.ConnectedClient>()

    override val send: Observer<Pair<ClientTracker.ConnectedClient, Any>>
    val sendSubject = PublishSubject.create<Pair<ClientTracker.ConnectedClient, Any>>()

    init {
        track = trackSubject
        drop = dropSubject
        dropped = droppedSubject
        send = sendSubject
    }
}