package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId

data class BurrowState(val server: ConnectionState, val clients: ClientsState)

data class ConnectionState(val address: String, val port: Int)

data class ClientsState(val connections: Map<ConnectionId, BurrowConnection>, val states: Map<ConnectionId, ClientState>)

data class ClientState(val lifecycle: ClientLifecycle, val nick: String?, val user: String?)

enum class ClientLifecycle {
    REGISTERING, CONNECTED, DISCONNECTED
}