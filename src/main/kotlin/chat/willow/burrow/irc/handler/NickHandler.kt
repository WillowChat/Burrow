package chat.willow.burrow.irc.handler

import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.state.ClientLifecycle
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.NickMessage

class NickHandler(private val clientTracker: IClientTracker) : BurrowHandler<NickMessage.Command>(NickMessage.Command.Parser) {

    private val LOGGER = loggerFor<NickHandler>()

    override fun handle(message: NickMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling NICK: $message")

        val lifecycle = clientTracker.lifecycleOf(id)

        when(lifecycle) {
            null -> LOGGER.warn("we're not tracking client yet, not doing anything: $id $message")

            ClientLifecycle.CONNECTED -> LOGGER.warn("client already connected, ignoring: $id $message")

            ClientLifecycle.REGISTERING -> {
                val state = clientTracker.registrationStateOf(id)
                if (state == null) {
                    LOGGER.warn("client not tracked as registered, ignoring: $id $message")
                    return
                }

                // TODO: check nobody else has this nickname, and that it isn't protected
                state.nick = message.nickname

                clientTracker.userAddedRegInfo(id)
            }

            ClientLifecycle.DISCONNECTED -> LOGGER.warn("client already disconnected, ignoring: $id $message")
        }
    }

}