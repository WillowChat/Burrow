package chat.willow.burrow.irc.handler

import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.prefix.PrefixParser

interface IBurrowHandler {

    val command: String

    fun on(message: IrcMessage)

}

abstract class BurrowHandler<in T>(override val command: String, private val parser: IMessageParser<T>) : IBurrowHandler {

    override fun on(message: IrcMessage) {
        if (message.command != command) {
            return
        }

        val parsedMessage = parser.parse(message) ?: return

        handle(parsedMessage)
    }

    abstract protected fun handle(message: T)

}

class NickHandler : BurrowHandler<NickMessage.Command>(NickMessage.command, NickMessage.Command.Parser) {

    private val LOGGER = loggerFor<NickHandler>()

    override fun handle(message: NickMessage.Command) {
        LOGGER.info("user wants to change their nick to ${message.nick}")
    }

}

abstract class BurrowSubcommandHandler(private val handlers: Map<String, BurrowHandler<*>>) : IBurrowHandler {

    private val LOGGER = loggerFor<BurrowSubcommandHandler>()

    override fun on(message: IrcMessage) {
        if (message.command != command) {
            return
        }

        if (message.parameters.isEmpty()) {
            return
        }

        val subcommand = message.parameters[0]

        val handler = handlers[subcommand]
        if (handler == null) {
            LOGGER.warn("no handler for subcommand $subcommand")
            return
        }

        handler.on(message)
    }

}

class CapHandler(private val lsHandler: BurrowHandler<CapLsMessage.Command>, handlers: Map<String, BurrowHandler<*>> = mapOf(CapLsMessage.command to lsHandler)) : BurrowSubcommandHandler(handlers) {

    override val command = "CAP"

    class CapLsHandler : BurrowHandler<CapLsMessage.Command>(CapLsMessage.command, CapLsMessage.Command.Parser) {

        private val LOGGER = loggerFor<CapLsHandler>()

        override fun handle(message: CapLsMessage.Command) {
            LOGGER.info("user sent cap ls command: $message")
        }

    }

}

interface IMessageParser<out T> {

    fun parse(message: IrcMessage): T?

}

abstract class MessageParser<out T>(private val command: String) : IMessageParser<T> {

    override fun parse(message: IrcMessage): T? {
        if (message.command != command) {
            return null
        }

        val components = IrcMessageComponents(tags = message.tags, prefix = message.prefix, parameters = message.parameters)

        return parseFromComponents(components)
    }

    abstract protected fun parseFromComponents(components: IrcMessageComponents): T?
}

interface IMessageSerialiser<in T> {

    fun serialise(message: T): IrcMessage?

}

abstract class MessageSerialiser<in T>(private val command: String) : IMessageSerialiser<T> {

    override fun serialise(message: T): IrcMessage? {
        val components = serialiseToComponents(message)

        return IrcMessage(command = command, tags = components.tags, prefix = components.prefix, parameters = components.parameters)
    }

    abstract protected fun serialiseToComponents(message: T): IrcMessageComponents
}

interface ICommand {

    val command: String

}

interface ISubcommand : ICommand {

    val subcommand: String
}

data class IrcMessageComponents(val parameters: List<String> = listOf(), val tags: Map<String, String?> = mapOf(), val prefix: String? = null)

abstract class SubcommandParser<out T>(private val subcommand: String) : IMessageParser<T> {

    override fun parse(message: IrcMessage): T? {
        if (message.parameters.isEmpty()) {
            return null
        }

        if (message.parameters.getOrNull(0) != subcommand) {
            return null
        }

        val parameters = message.parameters.drop(1)
        val components = IrcMessageComponents(tags = message.tags, prefix = message.prefix, parameters = parameters)

        return parseFromComponents(components)
    }

    abstract protected fun parseFromComponents(components: IrcMessageComponents): T?
}

abstract class SubcommandSerialiser<in T>(private val command: String, private val subcommand: String) : IMessageSerialiser<T> {

    override fun serialise(message: T): IrcMessage? {
        val components = serialiseToComponents(message)

        val parameters = listOf(subcommand) + components.parameters

        return IrcMessage(command = command, tags = components.tags, prefix = components.prefix, parameters = parameters)
    }

    abstract protected fun serialiseToComponents(message: T): IrcMessageComponents

}

object NickMessage : ICommand {

    override val command = "NICK"

    data class Command(val nick: String) {

        object Serialiser : MessageSerialiser<Command>(command) {

            override fun serialiseToComponents(message: Command): IrcMessageComponents {
                return IrcMessageComponents(parameters = listOf(message.nick))
            }
        }

        object Parser : MessageParser<Command>(command) {

            override fun parseFromComponents(components: IrcMessageComponents): Command? {
                if (components.parameters.isEmpty()) {
                    return null
                }

                val nick = components.parameters[0]

                return Command(nick)
            }

        }

    }

    data class Message(val source: Prefix, val nick: String) {

        object Parser : MessageParser<Message>(command) {

            override fun parseFromComponents(components: IrcMessageComponents): Message? {
                if (components.parameters.isEmpty()) {
                    return null
                }

                val messagePrefix = components.prefix ?: return null

                val prefix = PrefixParser.parse(messagePrefix) ?: return null
                val nick = components.parameters[0]

                return Message(prefix, nick)
            }
        }

    }

}

object CapLsMessage : ISubcommand {

    override val command = "CAP"
    override val subcommand = "LS"

    data class Command(val version: String?) {

        object Parser : SubcommandParser<Command>(subcommand) {

            override fun parseFromComponents(components: IrcMessageComponents): Command? {
                val version = components.parameters.getOrNull(0)

                return Command(version)
            }

        }

        object Serialiser : SubcommandSerialiser<Command>(command, subcommand) {

            override fun serialiseToComponents(message: Command): IrcMessageComponents {
                val parameters: List<String> = if (message.version == null) {
                    listOf()
                } else {
                    listOf(message.version)
                }

                return IrcMessageComponents(parameters = parameters)
            }

        }

    }

    data class Message(val target: String, val caps: List<String>)

}