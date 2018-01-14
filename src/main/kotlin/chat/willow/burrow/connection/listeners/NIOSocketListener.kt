package chat.willow.burrow.connection.listeners

import chat.willow.burrow.connection.IConnectionIdProvider
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import chat.willow.burrow.connection.network.INIOWrapper
import chat.willow.burrow.connection.network.ISelectionKeyWrapper
import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import io.reactivex.subjects.PublishSubject
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class NIOSocketListener(private val hostname: String,
                        private val port: Int,
                        private val nioWrapper: INIOWrapper,
                        private val incomingBuffer: ByteBuffer,
                        private val interruptedChecker: IInterruptedChecker,
                        private val idProvider: IConnectionIdProvider,
                        private val connectionPreparing: IConnectionPreparing
): IConnectionListening, IConnectionPreparing by connectionPreparing, Runnable {

    private val LOGGER = loggerFor<NIOSocketListener>()

    override val read = PublishSubject.create<IConnectionListening.Read>()
    override val accepted = PublishSubject.create<IConnectionListening.Accepted>()
    override val closed = PublishSubject.create<IConnectionListening.Closed>()

    private var processingThread: Thread? = null

    override fun start() {
        val socketAddress = InetSocketAddress(hostname, port)

        nioWrapper.setUp(socketAddress)

        processingThread = thread(start = true, name = "listener $hostname $port") {
            run()
        }
    }

    override fun run() {
        LOGGER.info("Starting...")

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

        LOGGER.info("Ended")
    }

    override fun tearDown() {
        nioWrapper.tearDown()
    }

    private fun accept(key: ISelectionKeyWrapper) {
        // todo: ip level ban?
        val (socket, clientKey) = nioWrapper.accept(key.original)

        val id = idProvider.next()
        nioWrapper.attach(id, clientKey)

        accepted.onNext(IConnectionListening.Accepted(id = id, primitiveConnection = socket))
    }

    private fun read(key: ISelectionKeyWrapper) {
        val (bytesRead, id) = nioWrapper.read(key.original, incomingBuffer)

        if (bytesRead < 0) {
            nioWrapper.close(key.original)

            closed.onNext(IConnectionListening.Closed(id = id))

            return
        }

        // bugfix: intentional copy - can't share buffer without being extremely sure where it is cleared and reused
        val bytes = incomingBuffer.array().copyOfRange(0, bytesRead)
        read.onNext(IConnectionListening.Read(id = id, bytes = bytes))
    }

    override fun toString(): String {
        return "NIOSocketListener(host=$hostname, port=$port)"
    }
}