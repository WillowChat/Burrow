package chat.willow.burrow.connection

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.connection.network.INetworkSocket
import chat.willow.burrow.connection.network.ISocketProcessor
import chat.willow.burrow.connection.network.SocketProcessor
import chat.willow.kale.IKale
import chat.willow.kale.IKaleRouter
import chat.willow.kale.irc.message.IrcMessageSerialiser
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

interface IConnectionTracker {

    fun <M : Any>send(id: ConnectionId, message: M)

    operator fun get(id: ConnectionId): BurrowConnection?

    val tracked: Observable<ConnectionTracker.Tracked>
    val dropped: Observable<ConnectionTracker.Dropped>

}

// todo: make most of this internal
data class BurrowConnection(val id: ConnectionId, val host: String, val socket: INetworkSocket, val accumulator: ILineAccumulator) {

    override fun toString(): String {
        return id.toString()
    }

}

class ConnectionTracker(socketProcessor: ISocketProcessor, val bufferSize: Int, var kale: IKale? = null): IConnectionTracker {

    private val LOGGER = loggerFor<ConnectionTracker>()

    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    data class Tracked(val connection: BurrowConnection)
    override val tracked: Observable<Tracked>
    private val trackedSubject = PublishSubject.create<Tracked>()

    data class Dropped(val id: ConnectionId)
    override val dropped: Observable<Dropped>
    private val droppedSubject = PublishSubject.create<Dropped>()

    init {
        tracked = trackedSubject
        dropped = droppedSubject

        socketProcessor.accepted
                .map(this::track)
                .subscribe(trackedSubject)

        socketProcessor.read
                .map { Pair(it.id, LineAccumulator.Input(bytes = it.buffer.array(), read = it.bytes)) }
                .subscribe({
                    connections[it.first]?.accumulator?.input?.onNext(it.second)
                })

        // todo: propagate to client tracker
        socketProcessor.closed
                .subscribe {
                    LOGGER.info("connection ${it.id} closed - dropping")

                    connections.remove(it.id)
                }

        socketProcessor.closed
                .map { Dropped(id = it.id) }
                .subscribe(dropped)
    }

    private fun track(accepted: SocketProcessor.Accepted): Tracked {
        val address = accepted.socket.socket.inetAddress.canonicalHostName

        val accumulator = LineAccumulator(bufferSize = bufferSize)

        val connection = BurrowConnection(accepted.id, host = address, socket = accepted.socket, accumulator = accumulator)

        connections[accepted.id] = connection

        LOGGER.info("tracked connection $connection")

        return Tracked(connection = connection)
    }

    override fun get(id: ConnectionId): BurrowConnection? {
        return connections[id]
    }

    override fun <M : Any> send(id: ConnectionId, message: M) {
        val ircMessage = kale?.serialise(message)
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

        send(id, line)
    }

    private fun send(id: ConnectionId, line: String) {
        val socket = connections[id]?.socket
        if (socket == null) {
            LOGGER.warn("tried to send something to missing client $id")
            return
        }

        val byteBuffer = Burrow.Server.UTF_8.encode(line + "\r\n")
        socket.write(byteBuffer)
    }

}