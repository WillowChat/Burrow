package chat.willow.burrow.connection

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.IBurrowKaleWrapper
import chat.willow.burrow.connection.network.INetworkSocket
import chat.willow.burrow.connection.network.ISocketProcessor
import chat.willow.burrow.connection.network.SocketProcessor
import chat.willow.kale.irc.message.IrcMessageSerialiser
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

interface IConnectionTracker {

    fun <M : Any>send(id: ConnectionId, message: M)

    operator fun get(id: ConnectionId): BurrowConnection?

}

// todo: make most of this internal
data class BurrowConnection(val id: ConnectionId, val host: String, val socket: INetworkSocket, val accumulator: ILineAccumulator) {

    override fun toString(): String {
        return id.toString()
    }

}

class ConnectionTracker(socketProcessor: ISocketProcessor, val bufferSize: Int, var kaleWrapper: IBurrowKaleWrapper? = null): IConnectionTracker {

    private val LOGGER = loggerFor<ConnectionTracker>()

    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    data class Tracked(val connection: BurrowConnection)
    val tracked: Observable<Tracked>
    private val trackedSubject = PublishSubject.create<Tracked>()

    init {
        tracked = trackedSubject

        socketProcessor.accepted
                .map(this::track)
                .subscribe(trackedSubject)

        socketProcessor.read
                .map { Pair(it.id, LineAccumulator.Input(bytes = it.buffer.array(), read = it.bytes)) }
                .subscribe({
                    connections[it.first]?.accumulator?.input?.onNext(it.second)
                })

        // todo: listen for socket drops
    }

    private fun track(accepted: SocketProcessor.Accepted): Tracked {
        val address = accepted.socket.socket.inetAddress.canonicalHostName

        val accumulator = LineAccumulator(bufferSize = bufferSize)
        accumulator.lines.subscribe({ kaleWrapper?.process(it, accepted.id) })

        val connection = BurrowConnection(accepted.id, host = address, socket = accepted.socket, accumulator = accumulator)

        connections[accepted.id] = connection

        LOGGER.info("tracked connection $connection")

        return Tracked(connection = connection)
    }

    override fun get(id: ConnectionId): BurrowConnection? {
        return connections[id]
    }

    override fun <M : Any> send(id: ConnectionId, message: M) {
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