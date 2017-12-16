package chat.willow.burrow

import chat.willow.burrow.connection.BurrowConnectionFactory
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.network.*
import chat.willow.burrow.helper.ThreadInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.state.*
import chat.willow.kale.*
import chat.willow.kale.helper.CaseMapping
import chat.willow.kale.helper.ICaseMapper
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.*
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.message.utility.RawMessage
import chat.willow.kale.irc.tag.KaleTagRouter
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.regex.Pattern
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
        val clientUseCase = ClientsUseCase(connectionTracker)
        val registrationUseCase = RegistrationUseCase(connectionTracker, clientUseCase)

        val supportedCaps = mapOf<String, String?>("cap-notify" to null)
        val clientTracker = ClientTracker(connections = connectionTracker, registrationUseCase = registrationUseCase, supportedCaps = supportedCaps, clientsUseCase = clientUseCase)
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
        router.register(PartMessage.Message::class, PartMessage.Message.Serialiser)
        router.register(PrivMsgMessage.Message::class, PrivMsgMessage.Message.Serialiser)


        // todo: reuse code without messageClass clashing b/c of typealias
        router.register(Rpl001Message.Message::class, Rpl001Message.Serialiser)
        router.register(Rpl433Message.Message::class, Rpl433Message.Serialiser)
        router.register(Rpl403Message.Message::class, Rpl403Message.Serialiser)
        router.register(Rpl353Message.Message::class, Rpl353Message.Message.Serialiser)

        router.register(PingMessage.Command::class, PingMessage.Command.Serialiser)
        router.register(PongMessage.Message::class, PongMessage.Message.Serialiser)
        router.register(CapMessage.Ls.Message::class, CapMessage.Ls.Message.Serialiser)
        router.register(CapMessage.Ack.Message::class, CapMessage.Ack.Message.Serialiser)
        router.register(CapMessage.Nak.Message::class, CapMessage.Nak.Message.Serialiser)


        router.register(RawMessage.Line::class, RawMessage.Line.Serialiser)

        return Kale(router, metadataFactory)
    }

    object Validation {
        val alphanumeric = Pattern.compile("^[a-zA-Z0-9]*$").asPredicate()
        val nick = Pattern.compile("^[a-zA-Z0-9]+[_]*$").asPredicate()
        val channel = Pattern.compile("^#[a-zA-Z0-9_]+$").asPredicate()
    }

    class Server(private val nioWrapper: INIOWrapper,
                 private val socketProcessor: ISocketProcessor) {

        companion object {
            val BUFFER_SIZE = 8192
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
            val MAPPER = object : ICaseMapper {
                override val current = CaseMapping.RFC1459

                override fun toLower(string: String): String {
                    return current.toLower(string)
                }

                override fun toString(): String {
                    return current.toString()
                }
            }
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