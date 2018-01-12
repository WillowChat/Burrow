package chat.willow.burrow.connection.listeners

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IPrimitiveConnection
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import io.reactivex.Observable
import java.nio.ByteBuffer

interface IConnectionListening: IConnectionPreparing {
    data class Read(val id: ConnectionId, val bytes: ByteArray)
    data class Accepted(val id: ConnectionId, val primitiveConnection: IPrimitiveConnection)
    data class Closed(val id: ConnectionId)

    val read: Observable<Read>
    val accepted: Observable<Accepted>
    val closed: Observable<Closed>

    fun start()
    fun tearDown()
}