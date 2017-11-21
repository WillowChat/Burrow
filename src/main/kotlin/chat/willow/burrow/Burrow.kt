package chat.willow.burrow

import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.line.IIrcMessageProcessor
import chat.willow.burrow.connection.line.LineProcessor
import chat.willow.burrow.irc.handler.*
import chat.willow.burrow.helper.ThreadInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.*
import chat.willow.burrow.connection.network.*
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IKaleMetadataFactory
import chat.willow.kale.IKaleRouter
import chat.willow.kale.KaleMetadataFactory
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.*
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
        val connectionTracker = ConnectionTracker(socketProcessor, bufferSize = Server.MAX_LINE_LENGTH)
        val clientTracker = ClientTracker(connectionTracker)
        val kaleWrapper = createKaleWrapper(BurrowRouter(), KaleMetadataFactory(KaleTagRouter()), clientTracker, connectionTracker)
        connectionTracker.kaleWrapper = kaleWrapper

        connectionTracker.tracked
                .map { it.connection }
                .subscribe(clientTracker.track)

        val lineProcessor = LineProcessor(interruptedChecker, kaleWrapper)
        val server = Server(nioWrapper, socketProcessor, lineProcessor)

        server.start()

        LOGGER.info("Ended")
    }

    fun createKaleWrapper(router: IKaleRouter<IBurrowIrcMessageHandler>, metadataFactory: IKaleMetadataFactory, clientTracker: IClientTracker, connectionTracker: IConnectionTracker): IBurrowKaleWrapper {
        val userHandler = UserHandler(clientTracker)
        val nickHandler = NickHandler(clientTracker)
        val capLsHandler = CapLsHandler(clientTracker)
        val joinHandler = JoinHandler(connectionTracker)
        val privMsgHandler = PrivMsgHandler(connectionTracker, clientTracker)
        val pingHandler = PingHandler(connectionTracker)
        val capHandler = BurrowSubcommandHandler(mapOf(CapMessage.Ls.subcommand to capLsHandler))

        router.register(UserMessage.command, userHandler)
        router.register(NickMessage.command, nickHandler)
        router.register(CapMessage.command, capHandler)
        router.register(JoinMessage.command, joinHandler)
        router.register(PrivMsgMessage.command, privMsgHandler)
        router.register(PingMessage.command, pingHandler)

        router.register(JoinMessage.Message::class, JoinMessage.Message.Serialiser)
        router.register(PrivMsgMessage.Message::class, PrivMsgMessage.Message.Serialiser)
        router.register(Rpl001MessageType::class, Rpl001Message.Serialiser)
        router.register(Rpl353Message.Message::class, Rpl353Message.Message.Serialiser)
        router.register(PingMessage.Command::class, PingMessage.Command.Serialiser)
        router.register(PongMessage.Message::class, PongMessage.Message.Serialiser)

        router.register(RawMessage.Line::class, RawMessage.Line.Serialiser)

        return BurrowKaleWrapper(router, metadataFactory)
    }

    class Server(private val nioWrapper: INIOWrapper,
                 private val socketProcessor: ISocketProcessor,
                 private val lineProcessor: IIrcMessageProcessor) {

        companion object {
            val BUFFER_SIZE = 8192
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
        }

        fun start() {
            val socketAddress = InetSocketAddress("0.0.0.0", 6667)

            nioWrapper.setUp(socketAddress)

            val lineProcessorThread = thread(name = "message processor", start = false) { lineProcessor.run() }
            val socketProcessorThread = thread(name = "socket processor", start = false) { socketProcessor.run() }

            lineProcessorThread.start()
            socketProcessorThread.start()

            // TODO: bail either thread out if either end
            lineProcessorThread.join()
            socketProcessorThread.join()
        }

    }

}