package chat.willow.burrow.connection

import chat.willow.burrow.ILineAccumulator
import chat.willow.burrow.network.INetworkSocket

data class BurrowConnection(val id: ConnectionId, val socket: INetworkSocket, val accumulator: ILineAccumulator) {

    override fun toString(): String {
        return id.toString()
    }

}