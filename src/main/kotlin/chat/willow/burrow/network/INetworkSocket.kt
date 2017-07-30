package chat.willow.burrow.network

import java.net.Socket

interface INetworkSocket {

    val isConnected: Boolean
    fun close()
    fun sendLine(line: String)
    val socket: Socket

}