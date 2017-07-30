package chat.willow.burrow.connection

import chat.willow.burrow.ILineAccumulatorListener
import chat.willow.burrow.ILineAccumulatorPool
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowKaleWrapper
import chat.willow.burrow.kale.IBurrowKaleWrapper
import chat.willow.burrow.network.INetworkSocket
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.irc.message.IrcMessageSerialiser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

typealias ConnectionId = Int

interface IConnectionTracker {

    fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowConnection
    fun drop(id: ConnectionId)
    fun <M : Any>send(id: ConnectionId, message: M)

    operator fun get(id: ConnectionId): BurrowConnection?
    operator fun minusAssign(id: ConnectionId)

}

class ConnectionTracker(private val lineAccumulatorPool: ILineAccumulatorPool, var kaleWrapper: IBurrowKaleWrapper? = null, var clientTracker: IClientTracker? = null): IConnectionTracker {

    private val LOGGER = loggerFor<ConnectionTracker>()

    private var nextConnectionId = AtomicInteger(0)
    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    override fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowConnection {
        val id = nextConnectionId.getAndIncrement()
        val accumulator = lineAccumulatorPool.next(id, listener)

        val connection =  BurrowConnection(id, socket = socket, accumulator = accumulator)

        connections[id] = connection

        // TODO: CME?
        clientTracker?.trackNewClient(id, host = socket.socket.inetAddress.toString())

        return connection
    }

    override fun drop(id: ConnectionId) {
        connections.remove(id)
    }

    override fun get(id: ConnectionId): BurrowConnection? {
        return connections[id]
    }

    override fun minusAssign(id: ConnectionId) {
        drop(id)
    }

    override fun <M : Any> send(id: ConnectionId, message: M) {
        val socket = connections[id]?.socket
        if (socket == null) {
            LOGGER.warn("tried to send something to missing client $id")
            return
        }

        val ircMessage = kaleWrapper?.serialise(message)
        if (ircMessage == null) {
            LOGGER.warn("failed to serialise message: $message")
            return
        }

        val line = IrcMessageSerialiser.serialise(ircMessage)
        if (line == null) {
            LOGGER.warn("failed to serialise IrcMessage: $ircMessage")
            return
        }

        LOGGER.info("$id ~ << $line")

        socket.sendLine(line)
    }

}