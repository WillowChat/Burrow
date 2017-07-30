package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId

data class BurrowState(val server: ConnectionState, val connections: Map<ConnectionId, BurrowConnection>)

data class ConnectionState(val address: String, val port: Int)