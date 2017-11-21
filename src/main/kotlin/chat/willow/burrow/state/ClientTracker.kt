package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject

data class RegisteringClientState(var nick: String? = null, var user: String? = null, val host: String)

data class ConnectedClientState(val prefix: Prefix)

enum class ClientLifecycle { REGISTERING, CONNECTED, DISCONNECTED }

interface IClientTracker {

    fun lifecycleOf(id: ConnectionId): ClientLifecycle?
    fun transitionToConnected(id: ConnectionId): ConnectedClientState?

    // TODO: Make registration tracker instead
    fun registrationStateOf(id: ConnectionId): RegisteringClientState?
    fun userAddedRegInfo(id: ConnectionId)

    fun connectedStateOf(id: ConnectionId): ConnectedClientState?
}

class ClientTracker(private val connectionTracker: IConnectionTracker): IClientTracker {

    private val LOGGER = loggerFor<ClientTracker>()

    private val registering = mutableMapOf<ConnectionId, RegisteringClientState>()
    private val connected = mutableMapOf<ConnectionId, ConnectedClientState>()
    private val lifecycles = mutableMapOf<ConnectionId, ClientLifecycle>()

    private val lineScheduler = Schedulers.single()

    val track = PublishSubject.create<BurrowConnection>()

    init {
        track.subscribe(this::track)
    }

    private fun track(connection: BurrowConnection) {
        // TODO: sanity check we're not reusing a connection id?

        val id = connection.id
        val host = connection.host

        val client = RegisteringClientState(host = host)
        lifecycles += (id to ClientLifecycle.REGISTERING)
        registering += (id to client)

        connection.accumulator.lines
                .observeOn(lineScheduler)
                .subscribe {
                    handle(connection, it)
                }

        //

        LOGGER.info("tracked client $connection")
    }

    private fun handle(connection: BurrowConnection, line: String) {
        val id = connection.id
        LOGGER.info("$id ~ >> $line")

        // either in registration mode or connected mode - switch out handlers?
    }

    override fun lifecycleOf(id: ConnectionId): ClientLifecycle? {
        val lifecycle = synchronized(this) {
            lifecycles[id]
        }

        return lifecycle
    }

    override fun transitionToConnected(id: ConnectionId): ConnectedClientState? {
        val registeringClient = registering[id] ?: return null

        val nick = registeringClient.nick ?: return null
        val user = registeringClient.user ?: return null
        val host = registeringClient.host

        val prefix = Prefix(nick = nick, user = user, host = host)

        val connectedClient = ConnectedClientState(prefix)

        registering -= id
        connected += (id to connectedClient)

        // TODO: don't do this manually

        LOGGER.info("$id transitioned to registered, sending 001")
        connectionTracker.send(id, Rpl001MessageType(source = "bunnies", target = nick, contents = "hello, world!"))

        return connectedClient
    }

    override fun registrationStateOf(id: ConnectionId): RegisteringClientState? {
        return registering[id]
    }

    override fun connectedStateOf(id: ConnectionId): ConnectedClientState? {
        return connected[id]
    }

    override fun userAddedRegInfo(id: ConnectionId) {
        val registrationState = registering[id] ?: return

        if (registrationState.user == null && registrationState.nick == null) {
            return
        }

        transitionToConnected(id)
    }

}