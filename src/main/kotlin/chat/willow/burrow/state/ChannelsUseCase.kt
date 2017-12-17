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
    fun isNameValid(name: String, existenceCheck: Boolean = false): Boolean

}

data class Channel(override val name: String,
                   val users: CaseInsensitiveNamedMap<ChannelUser>
                    = CaseInsensitiveNamedMap(mapper = Burrow.Server.MAPPER)): INamed

data class ChannelUser(val prefix: Prefix): INamed {
    override val name: String
        get() = prefix.nick
}

// todo: move in to Kale

object Rpl403Message : ICommand {

    override val command = "403"

    class Message(source: String, target: String, channel: String, content: String): RplSourceTargetChannelContent.Message(source, target, channel, content)
    object Parser : RplSourceTargetChannelContent.Parser(command)
    object Serialiser : RplSourceTargetChannelContent.Serialiser(command)
    object Descriptor : RplSourceTargetChannelContent.Descriptor(command, Parser)

}

class ChannelsUseCase(private val connections: IConnectionTracker, val clients: IClientsUseCase): IChannelsUseCase {

    private val LOGGER = loggerFor<ChannelsUseCase>()
    private val MAX_CHANNEL_LENGTH = 18 // todo: check

    override val channels = CaseInsensitiveNamedMap<Channel>(mapper = Burrow.Server.MAPPER)

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(JoinMessage.Command.Descriptor)
                .subscribe { handleJoin(it, client) }

        client.kale
                .observe(PartMessage.Command.Descriptor)
                .subscribe { handlePart(it, client) }

        clients.dropped
                .subscribe(this::handleClientDropped)
    }

    private fun handleJoin(observable: KaleObservable<JoinMessage.Command>, client: ClientTracker.ConnectedClient) {
        observable.message.channels.forEach {
            if (!isNameValid(it)) {
                sendNoSuchChannel(it, client)
            } else {
                handleValidJoin(it, client)
            }
        }
    }

    private fun sendNoSuchChannel(channelName: String, client: ClientTracker.ConnectedClient) {
        val noSuchChannelMessage = Rpl403Message.Message(source = "bunnies.", target = client.name, channel = channelName, content = "No such channel")
        connections.send(client.connection.id, noSuchChannelMessage)
    }

    private fun handleValidJoin(channelName: String, client: ClientTracker.ConnectedClient) {
        val channel = getOrCreateChannel(channelName)
        channel.users += ChannelUser(client.prefix)

        val joinMessage = JoinMessage.Message(source = client.prefix, channels = listOf(channel.name))
        connections.send(client.connection.id, joinMessage)

        // todo: split message when there's too many users
        // todo: permissions for users - @ etc as a prefix
        val users = channel.users.all.values.map { it.prefix.nick }
        val namReplyMessage = Rpl353Message.Message(source = "bunnies.", target = client.name, visibility = CharacterCodes.EQUALS.toString(), channel = channel.name, names = users)
        connections.send(client.connection.id, namReplyMessage)

        // todo: batch sending up?
        val otherUsers = (users.toSet() - client.name).mapNotNull { clients.lookUpClient(it) }
        otherUsers.forEach {
            connections.send(it.connection.id, JoinMessage.Message(source = client.prefix, channels = listOf(channel.name)))
        }
    }

    private fun getOrCreateChannel(name: String): Channel {
        var channel = channels[name]
        return if (channel == null) {
            channel = Channel(name = name)
            channels += channel

            channel
        } else {
            channel
        }
    }

    private fun handlePart(observable: KaleObservable<PartMessage.Command>, client: ClientTracker.ConnectedClient) {
        observable.message.channels.forEach {
            if (!isNameValid(it, existenceCheck = true)) {
                sendNoSuchChannel(it, client)
            } else {
                handleValidPart(it, client)
            }
        }
    }

    private fun handleValidPart(channelName: String, client: ClientTracker.ConnectedClient) {
        val channel = channels[channelName]
        if (channel == null) {
            LOGGER.warn("client left a nonexistent channel $channelName $client")
            return
        }

        channel.users -= client.name
        if (channel.users.all.isEmpty()) {
            channels -= channelName
        }

        val message = PartMessage.Message(source = client.prefix, channels = listOf(channelName))
        connections.send(client.connection.id, message)

        sendPartsToOtherChannelUsers(clientsParted = setOf(client.prefix), channel = channel)
    }

    private fun handleClientDropped(client: ClientTracker.ConnectedClient) {
        val channelsForUser = channelsForUser(client.name)

        channelsForUser.forEach { channel ->
            sendPartsToOtherChannelUsers(clientsParted = setOf(client.prefix), channel = channel)
        }
    }

    private fun sendPartsToOtherChannelUsers(clientsParted: Set<Prefix>, channel: Channel) {
        val users = channel.users.all.values.map { it.prefix.nick }
        val otherUsers = (users.toSet() - clientsParted.map { it.nick }).mapNotNull { clients.lookUpClient(it) }
        otherUsers.forEach { user ->
            clientsParted.forEach { partedClient ->
                connections.send(user.connection.id, PartMessage.Message(source = partedClient, channels = listOf(channel.name)))
            }
        }
    }

    override fun isNameValid(name: String, existenceCheck: Boolean): Boolean {
        val validation = !name.isEmpty() && name.length <= MAX_CHANNEL_LENGTH && channel.test(name)
        val existence = if (existenceCheck) {
            channels.contains(name)
        } else {
            true
        }

        return validation && existence
    }

    private fun channelsForUser(nick: String): List<Channel> {
        return channels.all.values.filter { it.users.contains(nick) }
    }

}