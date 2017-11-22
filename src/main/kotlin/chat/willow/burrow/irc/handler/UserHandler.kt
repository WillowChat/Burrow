package chat.willow.burrow.irc.handler

import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.UserMessage

class UserHandler(private val clientTracker: IClientTracker) : BurrowHandler<UserMessage.Command>(UserMessage.Command.Parser) {

    private val LOGGER = loggerFor<UserHandler>()

    override fun handle(message: UserMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling USER: $message")
    }

}