package chat.willow.burrow.connection.network

import java.net.Socket
import java.nio.ByteBuffer

interface INetworkSocket {

    val isConnected: Boolean
    fun close()
    val host: String
    val socket: Socket
    fun write(bytes: ByteBuffer)

}