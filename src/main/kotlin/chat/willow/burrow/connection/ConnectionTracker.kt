package chat.willow.burrow.connection

import chat.willow.burrow.Burrow
import chat.willow.burrow.Burrow.Server.Companion.MAX_LINE_LENGTH
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.helper.BurrowSchedulers
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.IrcMessageSerialiser
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.ReplaySubject
import java.util.concurrent.ConcurrentHashMap

interface IConnectionTracker {

    operator fun get(id: ConnectionId): BurrowConnection?
    fun addConnectionListener(listener: IConnectionListening)

    val tracked: Observable<ConnectionTracker.Tracked>
    val dropped: Observable<ConnectionTracker.Dropped>
    val lineReads: Map<ConnectionId, Observable<String>>

    val drop: Observer<ConnectionId>
    val send: Observer<Pair<ConnectionId, Any>>

}

class ConnectionTracker(
    var kale: IKale? = null,
    private val scheduler: Scheduler = BurrowSchedulers.unsharedSingleThread(name = "connections")
): IConnectionTracker {

    private val LOGGER = loggerFor<ConnectionTracker>()

    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    data class Tracked(val connection: BurrowConnection)
    override val tracked = PublishSubject.create<Tracked>()

    data class Dropped(val id: ConnectionId)
    override val dropped = PublishSubject.create<Dropped>()

    override val drop = PublishSubject.create<ConnectionId>()
    override val send = PublishSubject.create<Pair<ConnectionId, Any>>()

    private val socketReads = ConcurrentHashMap<ConnectionId, Observable<IConnectionListening.Read>>()
    override val lineReads = ConcurrentHashMap<ConnectionId, Observable<String>>()

    private val accumulators = ConcurrentHashMap<ConnectionId, ILineAccumulator>()

    init {
        drop
            .observeOn(scheduler)
            .subscribe {
                accumulators -= it
                connections[it]?.primitiveConnection?.close()
                connections -= it
                socketReads -= it
                lineReads -= it
            }

        send
            .observeOn(scheduler)
            .subscribe {
                this.send(it.first, it.second)
            }
    }

    override fun addConnectionListener(listener: IConnectionListening) {
        LOGGER.info("Adding listener: $listener")

        listener.accepted
            .subscribe { accepted ->
                val readStream = PublishSubject.create<IConnectionListening.Read>()
                socketReads += accepted.id to readStream

                // todo: more elegant way to replay events for registration, whilst setting up
                val lineReadStream = ReplaySubject.create<String>(4)
                lineReads += accepted.id to lineReadStream

                val accumulator = LineAccumulator(bufferSize = MAX_LINE_LENGTH)
                accumulators += accepted.id to accumulator

                accumulator.lines
                    .subscribe(lineReadStream::onNext)

                // todo: get rid of filter
                listener.read
                    .filter { it.id == accepted.id }
                    .subscribe(readStream::onNext)

                LOGGER.debug("Connection accepted - ${accepted.id}")
            }

            listener.accepted
                .subscribe { track(listener, it) }

        listener.closed
            .observeOn(scheduler)
            .doOnNext {
                LOGGER.info("Connection ${it.id} closed - dropping")
            }
            .map { it.id }
            .subscribe(drop::onNext)

        listener.closed
            .observeOn(scheduler)
            .map { Dropped(id = it.id) }
            .subscribe(dropped::onNext)
    }

    private fun track(listener: IConnectionListening, accepted: IConnectionListening.Accepted) {
        val reads = socketReads[accepted.id] ?: throw RuntimeException("Expected read channel to be set up")
        val accumulator = accumulators[accepted.id] ?: throw RuntimeException("Expected accumulator to be set up")

        listener.prepare(reads, accumulator, accepted, tracked, drop, connections)
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