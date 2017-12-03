package chat.willow.burrow

import chat.willow.burrow.connection.BurrowConnectionFactory
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.network.*
import chat.willow.burrow.helper.ThreadInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.state.ClientTracker
import chat.willow.kale.*
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.message.utility.RawMessage
import chat.willow.kale.irc.tag.KaleTagRouter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.concurrent.thread

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("Starting...")
        LOGGER.info("Support the development of this daemon through Patreon https://crrt.io/patreon 🎉")

        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val interruptedChecker = ThreadInterruptedChecker

        val buffer = ByteBuffer.allocate(Server.MAX_LINE_LENGTH)
        val socketProcessor = SocketProcessor(nioWrapper, buffer, interruptedChecker)
        val connectionTracker = ConnectionTracker(socketProcessor, bufferSize = Server.MAX_LINE_LENGTH, connectionFactory = BurrowConnectionFactory)
        val kale = createKale(KaleRouter(), KaleMetadataFactory(KaleTagRouter()))
        val clientTracker = ClientTracker(connections = connectionTracker) // todo: kalefactory
        connectionTracker.kale = kale

        connectionTracker.tracked
                .map { it.connection }
                .subscribe(clientTracker.track)

        connectionTracker.dropped
                .map { it.id }
                .subscribe(clientTracker.drop)

        val server = Server(nioWrapper, socketProcessor)

        server.start()

        LOGGER.info("Ended")
    }

    fun createKale(router: IKaleRouter, metadataFactory: IKaleMetadataFactory): IKale {
        router.register(JoinMessage.Message::class, JoinMessage.Message.Serialiser)
        router.register(PrivMsgMessage.Message::class, PrivMsgMessage.Message.Serialiser)
        router.register(Rpl001MessageType::class, Rpl001Message.Serialiser)
        router.register(Rpl353Message.Message::class, Rpl353Message.Message.Serialiser)
        router.register(PingMessage.Command::class, PingMessage.Command.Serialiser)
        router.register(PongMessage.Message::class, PongMessage.Message.Serialiser)
        router.register(CapMessage.Ls.Message::class, CapMessage.Ls.Message.Serialiser)
        router.register(CapMessage.Ack.Message::class, CapMessage.Ack.Message.Serialiser)
        router.register(CapMessage.Nak.Message::class, CapMessage.Nak.Message.Serialiser)

        router.register(RawMessage.Line::class, RawMessage.Line.Serialiser)

        return Kale(router, metadataFactory)
    }

    class Server(private val nioWrapper: INIOWrapper,
                 private val socketProcessor: ISocketProcessor) {

        companion object {
            val BUFFER_SIZE = 8192
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
        }

        fun start() {
            val socketAddress = InetSocketAddress("0.0.0.0", 6667)

            nioWrapper.setUp(socketAddress)

            val socketProcessorThread = thread(name = "socket processor", start = false) { socketProcessor.run() }

            socketProcessorThread.start()
            socketProcessorThread.join()
        }

    }

}