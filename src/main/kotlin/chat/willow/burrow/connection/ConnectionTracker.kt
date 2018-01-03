package chat.willow.burrow.connection

import chat.willow.burrow.Burrow
import chat.willow.burrow.Burrow.Server.Companion.MAX_LINE_LENGTH
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.IrcMessageSerialiser
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap

interface IConnectionTracker {

    operator fun get(id: ConnectionId): BurrowConnection?
    fun addConnectionListener(listener: IConnectionListening)

    val tracked: Observable<ConnectionTracker.Tracked>
    val dropped: Observable<ConnectionTracker.Dropped>
    val read: Observable<Pair<ConnectionId, String>>

    val drop: Observer<ConnectionId>
    val send: Observer<Pair<ConnectionId, Any>>

}

class ConnectionTracker(
    var kale: IKale? = null,
    private val socketScheduler: Scheduler = Schedulers.single()
): IConnectionTracker {

    private val LOGGER = loggerFor<ConnectionTracker>()

    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    data class Tracked(val connection: BurrowConnection)
    override val tracked = PublishSubject.create<Tracked>()

    data class Dropped(val id: ConnectionId)
    override val dropped = PublishSubject.create<Dropped>()

    override val drop = PublishSubject.create<ConnectionId>()
    override val send = PublishSubject.create<Pair<ConnectionId, Any>>()
    override val read = PublishSubject.create<Pair<ConnectionId, String>>()

    private val accumulators = ConcurrentHashMap<ConnectionId, ILineAccumulator>()

    init {
        drop
            .observeOn(socketScheduler)
            .subscribe {
                accumulators -= it
                connections[it]?.primitiveConnection?.close()
                connections -= it
            }

        send
            .observeOn(socketScheduler)
            .subscribe {
                this.send(it.first, it.second)
            }
    }

    override fun addConnectionListener(listener: IConnectionListening) {
        listener.accepted
            .subscribe { track(listener, it) }

        listener.closed
            .subscribe {
                LOGGER.info("connection ${it.id} closed - dropping")
                drop.onNext(it.id)
            }

        listener.closed
            .map { Dropped(id = it.id) }
            .subscribe(dropped)
    }

    private fun track(listener: IConnectionListening, accepted: IConnectionListening.Accepted) {
        val accumulator = LineAccumulator(bufferSize = MAX_LINE_LENGTH)

        accumulator.lines
            .map { accepted.id to it }
            .subscribe(read)

        accumulators += accepted.id to accumulator

        val input = listener.read
            .filter { it.id == accepted.id }
            .observeOn(socketScheduler)

        listener.prepare(input, accumulator, accepted, tracked, drop, connections)
    }

    override fun get(id: ConnectionId): BurrowConnection? {
        return connections[id]
    }

    private fun <M : Any> send(id: ConnectionId, message: M) {
        val ircMessage = kale?.serialise(message)
        if (ircMessage == null) {
            LOGGER.warn("failed to serialise message: $message")
            return
        }

        LOGGER.debug("$id ~ << $ircMessage")

        val line = IrcMessageSerialiser.serialise(ircMessage)
        if (line == null) {
            LOGGER.warn("failed to serialise IrcMessage: $ircMessage")
            return
        }

        send(id, line)
    }

    private fun send(id: ConnectionId, line: String) {
        val socket = connections[id]?.primitiveConnection
        if (socket == null) {
            LOGGER.warn("tried to send something to missing client $id")
            return
        }

        val byteBuffer = Burrow.Server.UTF_8.encode(line + "\r\n")
        socket.write(byteBuffer)
    }

}