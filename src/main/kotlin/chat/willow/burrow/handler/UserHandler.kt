package chat.willow.burrow.handler

import chat.willow.burrow.kale.BurrowHandler
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.state.ClientLifecycle
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.state.IClientTracker
import chat.willow.kale.IMetadataStore
import chat.willow.kale.irc.message.rfc1459.UserMessage

class UserHandler(private val clientTracker: IClientTracker) : BurrowHandler<UserMessage.Command>(UserMessage.Command.Parser) {

    private val LOGGER = loggerFor<UserHandler>()

    override fun handle(message: UserMessage.Command, metadata: IMetadataStore, id: ConnectionId) {
        LOGGER.info("$id ~ handling USER: $message")

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

                state.user = message.username

                clientTracker.userAddedRegInfo(id)
            }

            ClientLifecycle.DISCONNECTED -> LOGGER.warn("client already disconnected, ignoring: $id $message")
        }
    }

}