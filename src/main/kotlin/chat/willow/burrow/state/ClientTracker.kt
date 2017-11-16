package chat.willow.burrow.state

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix

data class RegisteringClientState(var nick: String? = null, var user: String? = null, val host: String)

data class ConnectedClientState(val prefix: Prefix)

enum class ClientLifecycle { REGISTERING, CONNECTED, DISCONNECTED }

interface IClientTracker {

    fun trackNewClient(id: ConnectionId, host: String): RegisteringClientState
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

    override fun trackNewClient(id: ConnectionId, host: String): RegisteringClientState {
        // TODO: sanity check we're not reusing a connection id?

        val client = RegisteringClientState(host = host)
        lifecycles += (id to ClientLifecycle.REGISTERING)
        registering += (id to client)

        return client
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