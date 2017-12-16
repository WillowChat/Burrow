package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.subjects.PublishSubject

interface IClientsUseCase {

    fun lookUpClient(nick: String): ClientTracker.ConnectedClient?

    val track: Observer<ClientTracker.ConnectedClient>
    val drop: Observer<ConnectionId>
    val dropped: Observable<ClientTracker.ConnectedClient>

}

class ClientsUseCase(val connections: IConnectionTracker): IClientsUseCase {

    private val LOGGER = loggerFor<ClientsUseCase>()

    private val channels = ChannelsUseCase(connections, this)
    private val ping = PingUseCase(connections)
    private val channelMessages = ChannelMessagesUseCase(connections, channels, this)

    private val clients = CaseInsensitiveNamedMap<ClientTracker.ConnectedClient>(mapper = Burrow.Server.MAPPER)

    override val track = PublishSubject.create<ClientTracker.ConnectedClient>()
    override val drop = PublishSubject.create<ConnectionId>()
    override val dropped = PublishSubject.create<ClientTracker.ConnectedClient>()

    init {
        track.subscribe(this::track)
        drop.subscribe(this::drop)
    }

    private fun track(client: ClientTracker.ConnectedClient) {
        connections.send(client.connection.id, Rpl001Message.Message(source = "bunnies.", target = client.prefix.nick, content = "welcome to burrow"))

        ping.track(client)
        channels.track(client)
        channelMessages.track(client)

        clients += client
        LOGGER.info("tracked client ${client.connection.id}")
    }

    override fun lookUpClient(nick: String): ClientTracker.ConnectedClient? {
        return clients[nick]
    }

    private fun drop(connectionId: ConnectionId) {
        // todo: optimise
        val client = clients.all.values.firstOrNull { connectionId == it.connection.id }
        if (client != null) {
            clients -= client.name
            dropped.onNext(client)
        }
    }
}