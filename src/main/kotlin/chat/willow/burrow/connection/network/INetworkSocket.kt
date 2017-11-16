package chat.willow.burrow.connection.network

import java.net.Socket

interface INetworkSocket {

    val isConnected: Boolean
    fun close()
    fun sendLine(line: String)
    val socket: Socket

}