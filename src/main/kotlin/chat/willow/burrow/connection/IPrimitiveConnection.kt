package chat.willow.burrow.connection

import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

interface IPrimitiveConnection {

    fun close()
    var host: String
    fun write(bytes: ByteBuffer)

}

typealias ConnectionId = Int

interface IConnectionIdProvider {
    fun next(): ConnectionId
}

object ConnectionIdProvider: IConnectionIdProvider {
    private var nextConnectionId = AtomicInteger(0)

    override fun next(): ConnectionId {
        // todo: verify wraparound
        return nextConnectionId.getAndIncrement()
    }
}