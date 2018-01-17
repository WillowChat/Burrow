package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.kale.core.message.KaleObservable
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage

interface IChannelMessagesUseCase {

    fun track(client: ClientTracker.ConnectedClient)

}

class ChannelMessagesUseCase(private val channels: IChannelsUseCase, private val clients: IClientsUseCase, private val serverName: INamed): IChannelMessagesUseCase {

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(PrivMsgMessage.Command.Descriptor)
                .subscribe { handlePrivMsg(it, client) }
    }

    private fun handlePrivMsg(observable: KaleObservable<PrivMsgMessage.Command>, client: ClientTracker.ConnectedClient) {
        val channelName = observable.message.target
        val message = observable.message.message

        if (!channels.isNameValid(channelName)) {
            sendInvalidChannelName(client, channelName)
        } else {
            handleValidPrivMsg(client, channelName, message)
        }

        // todo: validate message
        // check the client is in the channel
        // todo: check client has permissions to send messages to that channel
    }

    private fun sendCannotSendToChan(client: ClientTracker.ConnectedClient, channelName: String, message: String) {
        val messageToSend = KaleNumerics.CANNOTSENDTOCHAN.Message(source = serverName.name, target = client.name, channel = channelName, content = message)
        clients.send.onNext(client to messageToSend)
    }

    private fun sendInvalidChannelName(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "Invalid channel name")
    }

    private fun handleValidPrivMsg(client: ClientTracker.ConnectedClient, channelName: String, message: String) {
        val channel = channels.channels[channelName]
        if (channel == null) {
            sendNonexistentChannel(client, channelName)
            return
        }

        val userInChannel = channel.users.contains(client.name)
        if (!userInChannel) {
            sendUserNotInChannel(client, channelName)
            return
        }

        if (!isMessageValid(message)) {
            sendMessageNotValid(client, channelName)
            return
        }

        val otherUsers = channel.users.all.keys
                // todo: should probably be a capability of the storage
                .map(Burrow.Server.MAPPER::toLower)
                .filter { it != Burrow.Server.MAPPER.toLower(client.name) }
                .mapNotNull { clients.lookUpClient(it) }

        otherUsers.forEach { user ->
            val messageToSend = PrivMsgMessage.Message(source = client.prefix, target = channelName, message = message)
            clients.send.onNext(user to messageToSend)
        }

    }

    private fun sendNonexistentChannel(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "Channel doesn't exist")
    }

    private fun sendUserNotInChannel(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "You're not in that channel")
    }

    private fun isMessageValid(message: String): Boolean {
        if (message.isEmpty()) {
            return false
        }

        val messageAsBytes = message.toByteArray(charset = Burrow.Server.UTF_8)
        return messageAsBytes.size <= Burrow.Server.MAX_LINE_LENGTH
    }

    private fun sendMessageNotValid(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "That message was invalid")
    }

}