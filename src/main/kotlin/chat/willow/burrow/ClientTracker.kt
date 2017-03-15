package chat.willow.burrow

import chat.willow.burrow.network.INetworkSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

typealias ClientId = Int

data class BurrowClient(val id: ClientId, val socket: INetworkSocket, val accumulator: ILineAccumulator) {

    override fun toString(): String {
        return id.toString()
    }

}

interface IClientTracker {

    fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowClient
    fun drop(id: ClientId)

    operator fun get(id: ClientId): BurrowClient?
    operator fun minusAssign(id: ClientId)

}

class ClientTracker(private val lineAccumulatorPool: ILineAccumulatorPool): IClientTracker {

    private var nextClientId = AtomicInteger(0)
    private val clients: MutableMap<ClientId, BurrowClient> = ConcurrentHashMap()

    override fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowClient {
        val id = nextClientId.getAndIncrement()
        val accumulator = lineAccumulatorPool.next(id, listener)
        val client =  BurrowClient(id, socket = socket, accumulator = accumulator)

        clients[id] = client

        return client
    }

    override fun drop(id: ClientId) {
        clients.remove(id)
    }

    override fun get(id: ClientId): BurrowClient? {
        return clients[id]
    }

    override fun minusAssign(id: ClientId) {
        drop(id)
    }

}