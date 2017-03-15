package chat.willow.burrow.connection

import chat.willow.burrow.ILineAccumulator
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.network.INetworkSocket
import chat.willow.kale.IKale

data class BurrowConnection(val id: ConnectionId, val socket: INetworkSocket, val accumulator: ILineAccumulator, val kale: IKale) {

    override fun toString(): String {
        return id.toString()
    }

}