package chat.willow.burrow.irc.handler

import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage

class PingHandler(private val connectionTracker: IConnectionTracker) : BurrowHandler<PingMessage.Command>(PingMessage.Command.Parser) {

    private val LOGGER = loggerFor<PingHandler>()

    override fun handle(message: PingMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling PING: $message")

        // todo: be smarter about pings / connection lifecycle?
        connectionTracker.send(id, PongMessage.Message(token = message.token))
    }

}