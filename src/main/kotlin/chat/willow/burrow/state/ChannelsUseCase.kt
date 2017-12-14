package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.Burrow.Validation.channel
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.ICommand
import chat.willow.kale.KaleObservable
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.CharacterCodes
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PartMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
import chat.willow.kale.irc.message.rfc1459.rpl.RplSourceTargetChannelContent
import chat.willow.kale.irc.prefix.Prefix

interface IChannelsUseCase {

    val channels: CaseInsensitiveNamedMap<Channel>
    fun track(client: ClientTracker.ConnectedClient)

}

data class Channel(override val name: String,
                   val users: CaseInsensitiveNamedMap<ChannelUser>
                    = CaseInsensitiveNamedMap(mapper = Burrow.Server.MAPPER)): INamed

data class ChannelUser(val prefix: Prefix): INamed {
    override val name: String
        get() = prefix.nick
}

// todo: move in to Kale
typealias Rpl403MessageType = RplSourceTargetChannelContent.Message
object Rpl403Message : ICommand {

    override val command = "403"

    object Parser : RplSourceTargetChannelContent.Parser(command)
    object Serialiser : RplSourceTargetChannelContent.Serialiser(command)
    object Descriptor : RplSourceTargetChannelContent.Descriptor(command, Parser)

}

class ChannelsUseCase(private val connections: IConnectionTracker): IChannelsUseCase {

    private val LOGGER = loggerFor<ChannelsUseCase>()
    private val MAX_CHANNEL_LENGTH = 16 // todo: check

    override val channels = CaseInsensitiveNamedMap<Channel>(mapper = Burrow.Server.MAPPER)

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(JoinMessage.Command.Descriptor)
                .subscribe { handleJoin(it, client) }

        client.kale
                .observe(PartMessage.Command.Descriptor)
                .subscribe { handlePart(it, client) }
    }

    private fun handleJoin(observable: KaleObservable<JoinMessage.Command>, client: ClientTracker.ConnectedClient) {
        observable.message.channels.forEach {
            if (!validateChannelName(it)) {
                handleInvalidJoin(it, client)
            } else {
                handleValidJoin(it, client)
            }
        }
    }

    private fun handleInvalidJoin(channelName: String, client: ClientTracker.ConnectedClient) {
        val noSuchChannelMessage = Rpl403MessageType(source = "bunnies", target = client.name, channel = channelName, content = "No such channel")
        connections.send(client.connection.id, noSuchChannelMessage)
    }

    private fun handleValidJoin(channelName: String, client: ClientTracker.ConnectedClient) {
        val channel = if (channels.contains(channelName)) {
            channels[channelName]!! // todo: remove
        } else {
            val channel = Channel(name = channelName)
            channels += channel

            channel
        }

        channel.users += ChannelUser(client.prefix)

        val joinMessage = JoinMessage.Message(source = client.prefix, channels = listOf(channel.name))
        connections.send(client.connection.id, joinMessage)

        // todo: split message when there's too many users
        // todo: permissions for users - @ etc as a prefix
        val users = channel.users.all.values.map { it.prefix.nick }
        val namReplyMessage = Rpl353Message.Message(source = "bunnies", target = client.name, visibility = CharacterCodes.EQUALS.toString(), channel = channel.name, names = users)

        connections.send(client.connection.id, namReplyMessage)
    }

    private fun handlePart(observable: KaleObservable<PartMessage.Command>, client: ClientTracker.ConnectedClient) {
        // todo: validation
        observable.message.channels.forEach {
            val channel = channels[it]
            if (channel == null) {
                LOGGER.warn("client left a nonexistent channel $it $client")
            } else {
                channels -= channel.name
            }

            val message = PartMessage.Message(source = client.prefix, channels = listOf(it))
            connections.send(client.connection.id, message)
        }
    }

    private fun validateChannelName(name: String): Boolean {
        return !name.isEmpty() && name.length <= MAX_CHANNEL_LENGTH && channel.test(name)
    }

}