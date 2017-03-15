package chat.willow.burrow.network

interface INetworkSocket {

    val isConnected: Boolean
    fun close()

}