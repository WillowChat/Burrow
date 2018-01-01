package chat.willow.burrow.connection

interface IBurrowConnectionFactory {
    fun create(id: ConnectionId, primitiveConnection: IPrimitiveConnection): BurrowConnection
}

object BurrowConnectionFactory: IBurrowConnectionFactory {
    override fun create(id: ConnectionId, primitiveConnection: IPrimitiveConnection): BurrowConnection {
        return BurrowConnection(id, primitiveConnection)
    }
}

data class BurrowConnection(val id: ConnectionId, val primitiveConnection: IPrimitiveConnection) {

    val host: String
        get() = primitiveConnection.host

    override fun toString(): String {
        return id.toString()
    }

}