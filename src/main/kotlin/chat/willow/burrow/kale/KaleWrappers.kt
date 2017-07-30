package chat.willow.burrow.kale

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.*
import chat.willow.kale.irc.message.IMessageParser
import chat.willow.kale.irc.message.IMessageSerialiser
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.message.IrcMessageParser

interface IBurrowIrcMessageHandler {

    fun handle(message: IrcMessage, metadata: IMetadataStore, id: ConnectionId)

}

interface IBurrowMessageHandler<in T> {

    fun handle(message: T, metadata: IMetadataStore, id: ConnectionId)

}

class BurrowRouter: KaleRouter<IBurrowIrcMessageHandler>()

interface IBurrowKaleWrapper {

    fun process(line: String, id: ConnectionId)
    fun <M: Any> serialise(message: M): IrcMessage?

}

class BurrowKaleWrapper(private val router: IKaleRouter<IBurrowIrcMessageHandler>, private val metadataFactory: IKaleMetadataFactory): IBurrowKaleWrapper {

    private val LOGGER = loggerFor<BurrowKaleWrapper>()

    override fun process(line: String, id: ConnectionId) {
        val ircMessage = IrcMessageParser.parse(line)
        if (ircMessage == null) {
            LOGGER.warn("failed to parse line to IrcMessage: $line")
            return
        }

        val handler = router.handlerFor(ircMessage.command)
        if (handler == null) {
            LOGGER.debug("no handler for: ${ircMessage.command}")
            return
        }

        val metadata = metadataFactory.construct(ircMessage)

        handler.handle(ircMessage, metadata, id)
    }

    override fun <M: Any> serialise(message: M): IrcMessage? {
        @Suppress("UNCHECKED_CAST")
        val factory = router.serialiserFor(message::class.java) as? IMessageSerialiser<M>
        if (factory == null) {
            LOGGER.warn("failed to find factory for message serialisation: $message")
            return null
        }

        return factory.serialise(message)
    }

}

class BurrowKaleParseOnlyHandler<in T>(private val parser: IMessageParser<T>) : IBurrowIrcMessageHandler, IBurrowMessageHandler<T> {

    private val LOGGER = loggerFor<KaleParseOnlyHandler<T>>()

    override fun handle(message: IrcMessage, metadata: IMetadataStore, id: ConnectionId) {
        val parsedMessage = parser.parse(message) ?: return

        handle(parsedMessage, metadata, id)
    }

    override fun handle(message: T, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("no handler for: $message $metadata $id")
    }

}

abstract class BurrowHandler<in T>(private val parser: IMessageParser<T>) : IBurrowIrcMessageHandler, IBurrowMessageHandler<T> {

    override fun handle(message: IrcMessage, metadata: IMetadataStore, id: ConnectionId) {
        val parsedMessage = parser.parse(message) ?: return

        handle(parsedMessage, metadata, id)
    }

}

class BurrowSubcommandHandler(private val handlers: Map<String, IBurrowIrcMessageHandler>, val subcommandPosition: Int = 0) : IBurrowIrcMessageHandler {

    private val LOGGER = loggerFor<BurrowSubcommandHandler>()

    override fun handle(message: IrcMessage, metadata: IMetadataStore, id: ConnectionId) {
        if (message.parameters.size <= subcommandPosition) {
            return
        }

        val subcommand = message.parameters[subcommandPosition]

        val handler = handlers[subcommand]
        if (handler == null) {
            LOGGER.warn("no handler for subcommand $subcommand")
            return
        }

        handler.handle(message, metadata, id)
    }

}
