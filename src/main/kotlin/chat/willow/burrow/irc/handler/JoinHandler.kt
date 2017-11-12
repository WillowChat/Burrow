package chat.willow.burrow.irc.handler

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.message.utility.RawMessage
import chat.willow.kale.irc.prefix.prefix

class JoinHandler(private val connectionTracker: IConnectionTracker) : BurrowHandler<JoinMessage.Command>(JoinMessage.Command.Parser) {

    private val LOGGER = loggerFor<JoinHandler>()

    override fun handle(message: JoinMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling JOIN: $message")

        // TODO: uniques only
        message.channels.forEach {
            if (!channelNameIsValid(it)) {
                val failure = RawMessage.Line(":bunnies 403 name :no such channel")
                connectionTracker.send(id, failure)
            } else {
                val join = JoinMessage.Message(source = prefix("carrot"), channels = listOf(it))

                // todo: look up user's nick
                val namReply = Rpl353Message.Message(source = "bunnies", target = "name", visibility = "+", channel = it, names = listOf("carrot"))

                connectionTracker.send(id, join)
                connectionTracker.send(id, namReply)
            }
        }
    }

    private fun channelNameIsValid(name: String): Boolean {
        return name.startsWith("#")
    }

}