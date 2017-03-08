package chat.willow.burrow

import chat.willow.burrow.helper.*
import chat.willow.kale.irc.message.IrcMessageParser
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("starting server...")

        val lineAccumulatorPool = LineAccumulatorPool(bufferSize = Burrow.Server.MAX_LINE_LENGTH)
        val clientTracker = ClientTracker(lineAccumulatorPool)

        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val socketProcessorFactory = SocketProcessorFactory
        val server = Server(nioWrapper, socketProcessorFactory, clientTracker)

        server.start()

        LOGGER.info("server ended")
    }

    class Server(private val nioWrapper: INIOWrapper, private val socketProcessorFactory: ISocketProcessorFactory, private val clientTracker: IClientTracker) : ISocketProcessorDelegate, ILineAccumulatorListener {

        companion object {
            val BUFFER_SIZE = 4096
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
        }

        fun start() {
            val socketAddress = InetSocketAddress("0.0.0.0", 6667)
            nioWrapper.setUp(socketAddress)

            val socketProcessor = socketProcessorFactory.create(nioWrapper, buffer = ByteBuffer.allocate(MAX_LINE_LENGTH), delegate = this, interruptedChecker = ThreadInterruptedChecker)
            socketProcessor.run()
        }

        // ISocketProcessorDelegate

        override fun onAccepted(socket: INIOSocketChannelWrapper): ClientId {
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

        // TODO: Should this be on a "client object" ?

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

            // TODO: Add to processing queue

            val ircMessage = IrcMessageParser.parse(line)
            if (ircMessage == null) {
                LOGGER.warn("client $client sent malformed message, disconnecting them")
                disconnect(client.id)
            } else {
                LOGGER.info("client $client sent irc message: $ircMessage")
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