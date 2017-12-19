package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.utility.makeClient
import chat.willow.burrow.state.ClientsUseCase
import chat.willow.burrow.state.IClientsUseCase
import chat.willow.burrow.unit.connection.network.MockConnectionTracker
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.prefix.prefix
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class ClientUseCaseTests {

    private lateinit var sut: ClientsUseCase
    private lateinit var mockConnections: MockConnectionTracker

    private lateinit var sends: TestObserver<Pair<ConnectionId, Any>>

    @Before fun setUp() {
        mockConnections = MockConnectionTracker()

        sends = mockConnections.sendSubject.test()

        sut = ClientsUseCase(mockConnections)
    }

    @Test fun `when a client is tracked, they're sent an MOTD`() {
        val (_, client) = makeClient()

        sut.track.onNext(client)

        sends.assertValue(1 to Rpl001Message.Message(source = "bunnies.", target = "someone", content = "welcome to burrow"))
    }

    @Test fun `after tracking a client, we can look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertEquals("someone", result?.name)
    }

    @Test fun `if we haven't tracked a client, we can't look them up by username`() {
        val (_, client) = makeClient(prefix = prefix("someone else"))
        sut.track.onNext(client)

        val result = sut.lookUpClient(nick = "someone")

        assertNull(result)
    }

    @Test fun `we can't look up a dropped client`() {
        val (_, client) = makeClient(id = 1, prefix = prefix("someone"))
        sut.track.onNext(client)
        sut.drop.onNext(1)

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