package chat.willow.burrow.irc.handler

import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.NickMessage

class NickHandler(private val clientTracker: IClientTracker) : BurrowHandler<NickMessage.Command>(NickMessage.Command.Parser) {

    private val LOGGER = loggerFor<NickHandler>()

    override fun handle(message: NickMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling NICK: $message")
    }

}