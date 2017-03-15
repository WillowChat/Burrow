package chat.willow.burrow

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
        val clientTracker = ClientTracker(lineAccumulatorPool)

        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val socketProcessorFactory = SocketProcessorFactory
        val interruptedChecker = ThreadInterruptedChecker
        val messageProcessor = IrcMessageProcessor(interruptedChecker)
        val server = Server(nioWrapper, socketProcessorFactory, clientTracker, messageProcessor, interruptedChecker)

        server.start()

        LOGGER.info("server ended")
    }

    class Server(private val nioWrapper: INIOWrapper, private val socketProcessorFactory: ISocketProcessorFactory, private val clientTracker: IClientTracker, private val messageProcessor: IIrcMessageProcessor, private val interruptedChecker: IInterruptedChecker) : ISocketProcessorDelegate, ILineAccumulatorListener {

        companion object {
            val BUFFER_SIZE = 4096
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

        override fun onAccepted(socket: INetworkSocket): ClientId {
            val client = clientTracker.track(socket, listener = this)

            LOGGER.info("accepted connection $client")

            return client.id
        }

        override fun onDisconnected(id: ClientId) {
            val client = clientTracker[id]
            if (client == null) {
                LOGGER.warn("disconnected client that we're not tracking? $id")
                return
            }

            clientTracker -= client.id

            LOGGER.info("disconnected $client")
        }

        override fun onRead(id: ClientId, buffer: ByteBuffer, bytesRead: Int) {
            val client = clientTracker[id]
            if (client == null) {
                LOGGER.warn("read bytes from a client we're not tracking: $id")
                return
            }

            LOGGER.info("client $client sent $bytesRead bytes, accumulating...")

            client.accumulator.add(buffer.array(), bytesRead)
        }

        // ILineAccumulatorListener

        override fun onBufferOverran(id: ClientId) {
            LOGGER.info("client $id onBufferOverran, disconnecting them")
            disconnect(id)
        }

        override fun onLineAccumulated(id: ClientId, line: String) {
            val client = clientTracker[id]
            if (client == null) {
                LOGGER.warn("accumulated a line for a client we don't know about: $id - $line")
                return
            }

            LOGGER.info("client $client sent line: $line")

            val ircMessage = IrcMessageParser.parse(line)
            if (ircMessage == null) {
                LOGGER.warn("client $client sent malformed message, disconnecting them")
                disconnect(client.id)
            } else {
                messageProcessor += (client to ircMessage)
            }
        }

        private fun disconnect(id: ClientId) {
            val client = clientTracker[id]
            if (client == null) {
                LOGGER.warn("couldn't disconnect client because we're not tracking them $id")
                return
            }

            if (!client.socket.isConnected) {
                LOGGER.warn("tried to disconnect client whose socket isn't connected, untracking them anyway $id")
                clientTracker -= client.id
                return
            }

            client.socket.close()
            clientTracker -= client.id
        }
    }

}