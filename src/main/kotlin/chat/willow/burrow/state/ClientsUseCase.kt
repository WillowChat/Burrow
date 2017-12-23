package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.subjects.PublishSubject

interface IClientsUseCase {

    fun lookUpClient(nick: String): ClientTracker.ConnectedClient?

    val track: Observer<ClientTracker.ConnectedClient>
    val drop: Observer<ConnectionId>
    val dropped: Observable<ClientTracker.ConnectedClient>
    val send: Observer<Pair<ClientTracker.ConnectedClient, Any>>

}

class ClientsUseCase(connections: IConnectionTracker): IClientsUseCase {

    private val LOGGER = loggerFor<ClientsUseCase>()

    override val track = PublishSubject.create<ClientTracker.ConnectedClient>()
    override val drop = PublishSubject.create<ConnectionId>()
    override val dropped = PublishSubject.create<ClientTracker.ConnectedClient>()
    override val send = PublishSubject.create<Pair<ClientTracker.ConnectedClient, Any>>()

    private val channels = ChannelsUseCase(this)
    private val ping = PingUseCase(this)
    private val channelMessages = ChannelMessagesUseCase(channels, this)

    private val clients = CaseInsensitiveNamedMap<ClientTracker.ConnectedClient>(mapper = Burrow.Server.MAPPER)

    init {
        track.subscribe(this::track)
        drop.subscribe(this::drop)
        send
                .map { it.first.connectionId to it.second }
                .subscribe(connections.send)
    }

    private fun track(client: ClientTracker.ConnectedClient) {
        send.onNext(client to KaleNumerics.WELCOME.Message(source = "bunnies.", target = client.prefix.nick, content = "welcome to burrow"))

        ping.track(client)
        channels.track(client)
        channelMessages.track(client)

        clients += client
        LOGGER.info("tracked client ${client.connectionId}")
    }

    override fun lookUpClient(nick: String): ClientTracker.ConnectedClient? {
        return clients[nick]
    }

    private fun drop(connectionId: ConnectionId) {
        // todo: optimise
        val client = clients.all.values.firstOrNull { connectionId == it.connectionId }
        if (client != null) {
            clients -= client.name
            dropped.onNext(client)
        }
    }
}