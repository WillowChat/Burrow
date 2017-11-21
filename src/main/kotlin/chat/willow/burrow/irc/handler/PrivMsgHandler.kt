package chat.willow.burrow.irc.handler

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import chat.willow.kale.irc.prefix.prefix

class PrivMsgHandler(private val connectionTracker: IConnectionTracker, private val clientTracker: IClientTracker) : BurrowHandler<PrivMsgMessage.Command>(PrivMsgMessage.Command.Parser) {

    private val LOGGER = loggerFor<PrivMsgHandler>()

    override fun handle(message: PrivMsgMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling PrivMsg: $message")

        val client = clientTracker.connectedStateOf(id)
        if (client == null) {
            LOGGER.warn("got a privmsg from a client we aren't tracking?")
            return
        }

        if (message.message == "hello burrow") {
            connectionTracker.send(id, PrivMsgMessage.Message(source = prefix("burrow"), target = message.target, message = "hello, ${client.prefix.nick} ✨"))
        }
    }

    private fun channelNameIsValid(name: String): Boolean {
        return name.startsWith("#")
    }

}