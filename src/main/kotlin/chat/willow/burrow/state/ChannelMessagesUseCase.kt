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
        }

        // todo: validate message
        // check the client is in the channel
        // todo: check client has permissions to send messages to that channel
    }

    private fun sendInvalidChannelName(client: ClientTracker.ConnectedClient, channelName: String) {
        val message = Rpl404MessageType(source = "bunnies", target = client.name, channel = channelName, content = "Invalid channel name")
        connections.send(client.connection.id, message)
    }

}