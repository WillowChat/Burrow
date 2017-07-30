package chat.willow.burrow.handler

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.extension.cap.CapMessage

class CapLsHandler(private val clientTracker: IClientTracker) : BurrowHandler<CapMessage.Ls.Command>(CapMessage.Ls.Command.Parser) {

    private val LOGGER = loggerFor<CapLsHandler>()

    override fun handle(message: CapMessage.Ls.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling CAP LS: $message")

        // TODO: don't ignore
    }

}