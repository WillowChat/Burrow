package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.kale.ICommand
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.rfc1459.PrivMsgMessage
import chat.willow.kale.irc.message.rfc1459.rpl.RplSourceTargetChannelContent

interface IChannelMessagesUseCase {

    fun track(client: ClientTracker.ConnectedClient)

}

// todo: move in to Kale
typealias Rpl404MessageType = RplSourceTargetChannelContent.Message
object Rpl404Message : ICommand {

    override val command = "404"

    object Parser : RplSourceTargetChannelContent.Parser(command)
    object Serialiser : RplSourceTargetChannelContent.Serialiser(command)
    object Descriptor : RplSourceTargetChannelContent.Descriptor(command, Parser)

}

class ChannelMessagesUseCase(private val connections: IConnectionTracker, private val channels: IChannelsUseCase): IChannelMessagesUseCase {

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
        val messageToSend = Rpl404MessageType(source = "bunnies", target = client.name, channel = channelName, content = message)
        connections.send(client.connection.id, messageToSend)
    }

    private fun sendInvalidChannelName(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "Invalid channel name")
    }

    private fun handleValidPrivMsg(client: ClientTracker.ConnectedClient, channelName: String, message: String) {
        val channel = channels.channels[channelName]
        if (channel == null) {
            sendNonexistentChannel(client, channelName)
        } else {
            val userInChannel = channel.users.contains(client.name)
            if (!userInChannel) {
                sendUserNotInChannel(client, channelName)
            }
        }
    }

    private fun sendNonexistentChannel(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "Channel doesn't exist")
    }

    private fun sendUserNotInChannel(client: ClientTracker.ConnectedClient, channelName: String) {
        sendCannotSendToChan(client, channelName, "You're not in that channel")
    }

}