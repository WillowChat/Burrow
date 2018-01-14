package chat.willow.burrow

import chat.willow.burrow.configuration.BurrowConfig
import chat.willow.burrow.configuration.ListenerConfig
import chat.willow.burrow.connection.BurrowConnectionFactory
import chat.willow.burrow.connection.ConnectionIdProvider
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.NIOSocketListener
import chat.willow.burrow.connection.listeners.preparing.HaproxyConnectionPreparing
import chat.willow.burrow.connection.listeners.preparing.PlainConnectionPreparing
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.NIOWrapper
import chat.willow.burrow.connection.network.SelectorFactory
import chat.willow.burrow.helper.ThreadInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.ClientsUseCase
import chat.willow.burrow.state.RegistrationUseCase
import chat.willow.kale.*
import chat.willow.kale.core.tag.KaleTagRouter
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.helper.CaseMapping
import chat.willow.kale.helper.ICaseMapper
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.*
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.message.utility.RawMessage
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.regex.Pattern

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("Starting...")
        LOGGER.info("Support the development of this IRC server through Patreon https://crrt.io/patreon 🎉")

        if (!args.isEmpty()) {
            LOGGER.warn("Configuration of Burrow is done by editing `burrow.yaml` (don't pass program arguments)")
        }

        LOGGER.info("Loading configuration...")
        val config = BurrowConfig()

        val listeners = config.server.listen.map {
            when (it.type) {
                ListenerConfig.Type.PLAINTEXT -> createPlaintextListener(it.host, it.port)
                ListenerConfig.Type.HAPROXY_V2 -> createHaproxyListener(it.host, it.port)
            }
        }

        if (listeners.isEmpty()) {
            LOGGER.error("You must configure at least one listener")
            return
        }

        val connectionTracker = ConnectionTracker()
        listeners.forEach(connectionTracker::addConnectionListener)

        val kale = createKale(KaleRouter(), KaleMetadataFactory(KaleTagRouter()))
        val clientUseCase = ClientsUseCase(connectionTracker, config.server, config.network)
        val registrationUseCase = RegistrationUseCase(connectionTracker, clientUseCase, config.server)

        val supportedCaps = mapOf<String, String?>("cap-notify" to null)
        val clientTracker = ClientTracker(connections = connectionTracker, registrationUseCase = registrationUseCase, supportedCaps = supportedCaps, clientsUseCase = clientUseCase)
        connectionTracker.kale = kale

        connectionTracker.tracked
                .map { it.connection }
                .subscribe(clientTracker.track::onNext)

        connectionTracker.dropped
                .map { it.id }
                .subscribe(clientTracker.drop::onNext)

        val server = Server(listeners)

        server.start()

        LOGGER.info("Ended")
    }

    private fun createHaproxyListener(host: String, port: Int): IConnectionListening {
        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val interruptedChecker = ThreadInterruptedChecker

        val buffer = ByteBuffer.allocate(Server.MAX_LINE_LENGTH)

        val haproxyPreparing =
            HaproxyConnectionPreparing(BurrowConnectionFactory, HaproxyHeaderDecoder())

        return NIOSocketListener(
            host,
            port,
            nioWrapper,
            buffer,
            interruptedChecker,
            ConnectionIdProvider,
            haproxyPreparing
        )
    }

    private fun createPlaintextListener(host: String, port: Int): IConnectionListening {
        val selectorFactory = SelectorFactory
        val nioWrapper = NIOWrapper(selectorFactory)
        val interruptedChecker = ThreadInterruptedChecker

        val buffer = ByteBuffer.allocate(Server.MAX_LINE_LENGTH)

        val plainPreparing =
            PlainConnectionPreparing(BurrowConnectionFactory)

        return NIOSocketListener(
            host,
            port,
            nioWrapper,
            buffer,
            interruptedChecker,
            ConnectionIdProvider,
            plainPreparing
        )
    }

    private fun createKale(router: IKaleRouter, metadataFactory: IKaleMetadataFactory): IKale {
        router.register(JoinMessage.Message::class, JoinMessage.Message.Serialiser)
        router.register(PartMessage.Message::class, PartMessage.Message.Serialiser)
        router.register(PrivMsgMessage.Message::class, PrivMsgMessage.Message.Serialiser)

        router.register(KaleNumerics.WELCOME.Message::class, KaleNumerics.WELCOME.Serialiser)
        router.register(KaleNumerics.NICKNAMEINUSE.Message::class, KaleNumerics.NICKNAMEINUSE.Serialiser)
        router.register(KaleNumerics.ERRONEOUSNICKNAME.Message::class, KaleNumerics.ERRONEOUSNICKNAME.Serialiser)
        router.register(KaleNumerics.NOSUCHCHANNEL.Message::class, KaleNumerics.NOSUCHCHANNEL.Serialiser)
        router.register(KaleNumerics.ENDOFNAMES.Message::class, KaleNumerics.ENDOFNAMES.Serialiser)
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

    class Server(private val listeners: List<IConnectionListening>) {

        companion object {
            val BUFFER_SIZE = 8192
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
            val MAPPER = object : ICaseMapper {
                override val current = CaseMapping.STRICT_RFC1459

                override fun toLower(string: String): String {
                    return current.toLower(string)
                }

                override fun toString(): String {
                    return current.toString()
                }
            }
        }

        private val monitor = java.lang.Object()

        fun start() {
            LOGGER.info("Starting listeners...")

            listeners.forEach {
                it.start()
            }

            synchronized(monitor) {
                try {
                    while(true) {
                        monitor.wait()
                    }
                } catch (exception: Exception) {
                    when (exception) {
                        is InterruptedException -> LOGGER.info("Burrow stopping cleanly")
                        else -> LOGGER.error("Burrow stopping due to an exception", exception)
                    }
                }
            }

            listeners.forEach {
                LOGGER.info("Tearing down listener: $it")
                it.tearDown()
            }
        }

    }

}