package chat.willow.burrow

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.KaleFactory
import chat.willow.burrow.helper.*
import chat.willow.burrow.network.*
import chat.willow.kale.irc.message.IrcMessageParser
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.concurrent.thread

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("starting server...")

        val lineAccumulatorPool = LineAccumulatorPool(bufferSize = Burrow.Server.MAX_LINE_LENGTH)
        val kaleFactory = KaleFactory
        val clientTracker = ConnectionTracker(lineAccumulatorPool, kaleFactory)

        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val socketProcessorFactory = SocketProcessorFactory
        val interruptedChecker = ThreadInterruptedChecker
        val messageProcessor = LineProcessor(interruptedChecker)
        val server = Server(nioWrapper, socketProcessorFactory, clientTracker, messageProcessor, interruptedChecker)

        server.start()

        LOGGER.info("server ended")
    }

    class Server(private val nioWrapper: INIOWrapper, private val socketProcessorFactory: ISocketProcessorFactory, private val connectionTracker: IConnectionTracker, private val messageProcessor: IIrcMessageProcessor, private val interruptedChecker: IInterruptedChecker) : ISocketProcessorDelegate, ILineAccumulatorListener {

        companion object {
            val BUFFER_SIZE = 8192
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
        }

        fun start() {
            val socketAddress = InetSocketAddress("0.0.0.0", 6667)
            nioWrapper.setUp(socketAddress)

            val socketProcessor = socketProcessorFactory.create(nioWrapper, buffer = ByteBuffer.allocate(MAX_LINE_LENGTH), delegate = this, interruptedChecker = interruptedChecker)

            val messageProcessorThread = thread(start = false) { messageProcessor.run() }
            val socketProcessorThread = thread(start = false) { socketProcessor.run() }

            messageProcessorThread.start()
            socketProcessorThread.start()

            // TODO: bail either thread out if either end
            messageProcessorThread.join()
            socketProcessorThread.join()
        }

        // ISocketProcessorDelegate

        override fun onAccepted(socket: INetworkSocket): ConnectionId {
            val client = connectionTracker.track(socket, listener = this)

            LOGGER.info("accepted connection $client")

            return client.id
        }

        override fun onDisconnected(id: ConnectionId) {
            val client = connectionTracker[id]
            if (client == null) {
                LOGGER.warn("disconnected connection that we're not tracking? $id")
                return
            }

            connectionTracker -= client.id

            LOGGER.info("disconnected $client")
        }

        override fun onRead(id: ConnectionId, buffer: ByteBuffer, bytesRead: Int) {
            val client = connectionTracker[id]
            if (client == null) {
                LOGGER.warn("read bytes from a connection we're not tracking: $id")
                return
            }

            LOGGER.info("connection $client sent $bytesRead bytes, accumulating...")

            client.accumulator.add(buffer.array(), bytesRead)
        }

        // ILineAccumulatorListener

        override fun onBufferOverran(id: ConnectionId) {
            LOGGER.info("connection $id onBufferOverran, disconnecting them")
            disconnect(id)
        }

        override fun onLineAccumulated(id: ConnectionId, line: String) {
            val client = connectionTracker[id]
            if (client == null) {
                LOGGER.warn("accumulated a line for a connection we don't know about: $id - $line")
                return
            }

            LOGGER.info("connection $client sent line: $line")

            messageProcessor += (client to line)
        }

        private fun disconnect(id: ConnectionId) {
            val client = connectionTracker[id]
            if (client == null) {
                LOGGER.warn("couldn't disconnect connection because we're not tracking them $id")
                return
            }

            if (!client.socket.isConnected) {
                LOGGER.warn("tried to disconnect connection whose socket isn't connected, untracking them anyway $id")
                connectionTracker -= client.id
                return
            }

            client.socket.close()
            connectionTracker -= client.id
        }
    }

}