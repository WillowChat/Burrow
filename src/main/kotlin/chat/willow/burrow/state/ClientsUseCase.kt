package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.BurrowSchedulers
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.helper.INamed
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.subjects.PublishSubject

interface IClientsUseCase {

    fun lookUpClient(nick: String): ClientTracker.ConnectedClient?

    val track: Observer<ClientTracker.ConnectedClient>
    val drop: Observer<ConnectionId>
    val dropped: Observable<ClientTracker.ConnectedClient>
    val send: Observer<Pair<ClientTracker.ConnectedClient, Any>>

}

class ClientsUseCase(connections: IConnectionTracker,
                     val serverName: INamed,
                     val networkName: INamed,
                     scheduler: Scheduler = BurrowSchedulers.unsharedSingleThread(name = "usecase")): IClientsUseCase {

    private val LOGGER = loggerFor<ClientsUseCase>()

    override val track = PublishSubject.create<ClientTracker.ConnectedClient>()
    override val drop = PublishSubject.create<ConnectionId>()
    override val dropped = PublishSubject.create<ClientTracker.ConnectedClient>()
    override val send = PublishSubject.create<Pair<ClientTracker.ConnectedClient, Any>>()

    private val channels = ChannelsUseCase(this, serverName)
    private val ping = PingUseCase(this)
    private val channelMessages = ChannelMessagesUseCase(channels, this, serverName)

    private val clients = CaseInsensitiveNamedMap<ClientTracker.ConnectedClient>(mapper = Burrow.Server.MAPPER)

    init {
        val sharedTrack = track.observeOn(scheduler)
            .share()

        setUpTracking(sharedTrack)

        drop
            .observeOn(scheduler)
            .subscribe(this::drop)

        send
            .observeOn(scheduler)
            .map { it.first.connectionId to it.second }
            .subscribe(connections.send::onNext)
    }

    private fun setUpTracking(observable: Observable<ClientTracker.ConnectedClient>) {
        val message = "Welcome to ${networkName.name}"
        observable.map {
                it to KaleNumerics.WELCOME.Message(source = serverName.name, target = it.prefix.nick, content = message)
            }
            .subscribe(send)

        observable.subscribe(ping::track)
        observable.subscribe(channels::track)
        observable.subscribe(channelMessages::track)

        observable.subscribe {
            clients += it
            LOGGER.info("Tracked client ${it.connectionId}")
        }

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