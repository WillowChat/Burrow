package chat.willow.burrow.connection.network

import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

typealias ConnectionId = Int

interface ISocketProcessor: Runnable {
    val read: Observable<SocketProcessor.Read>
    val accepted: Observable<SocketProcessor.Accepted>
    val closed: Observable<SocketProcessor.Closed>

    fun tearDown()
}

class SocketProcessor(private val nioWrapper: INIOWrapper, val incomingBuffer: ByteBuffer, private val interruptedChecker: IInterruptedChecker): ISocketProcessor {

    private val LOGGER = loggerFor<SocketProcessor>()

    data class Read(val id: ConnectionId, val buffer: ByteBuffer, val bytes: Int)
    override val read: Observable<Read>
    private val readSubject = PublishSubject.create<Read>()

    data class Accepted(val id: ConnectionId, val socket: INetworkSocket)
    override val accepted: Observable<Accepted>
    private val acceptedSubject = PublishSubject.create<Accepted>()

    data class Closed(val id: ConnectionId)
    override val closed: Observable<Closed>
    private val closedSubject = PublishSubject.create<Closed>()

    // todo: verify wraparound
    private var nextConnectionId = AtomicInteger(0)

    init {
        read = readSubject
        accepted = acceptedSubject
        closed = closedSubject
    }

    override fun run() {
        LOGGER.info("starting...")

        while (!interruptedChecker.isInterrupted) {
            val keys = nioWrapper.select()

            for (key in keys) {
                when {
                    key.isAcceptable -> accept(key)
                    key.isReadable -> read(key)
                }
            }

            nioWrapper.clearSelectedKeys()
        }

        nioWrapper.tearDown()
        LOGGER.info("thread interrupted, bailing out")
    }

    override fun tearDown() {
        nioWrapper.tearDown()
    }

    private fun accept(key: ISelectionKeyWrapper) {
        // todo: ip level ban?
        val (socket, clientKey) = nioWrapper.accept(key.original)

        val id = nextConnectionId.getAndIncrement()
        acceptedSubject.onNext(Accepted(id = id, socket = socket))

        nioWrapper.attach(id, clientKey)
    }

    private fun read(key: ISelectionKeyWrapper) {
        val (bytesRead, id) = nioWrapper.read(key.original, incomingBuffer)

        if (bytesRead < 0) {
            nioWrapper.close(key.original)

            closedSubject.onNext(Closed(id = id))

            return
        }

        readSubject.onNext(Read(id = id, buffer = incomingBuffer, bytes = bytesRead))
    }

}