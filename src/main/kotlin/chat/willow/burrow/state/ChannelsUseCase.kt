package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.Burrow.Validation.channel
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.core.message.KaleObservable
import chat.willow.kale.generated.KaleNumerics
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.CharacterCodes
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PartMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl353Message
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

class ChannelsUseCase(private val clients: IClientsUseCase,
                      private val serverName: INamed): IChannelsUseCase {

    private val LOGGER = loggerFor<ChannelsUseCase>()
    private val MAX_CHANNEL_LENGTH = 18 // todo: check

    override val channels = CaseInsensitiveNamedMap<Channel>(mapper = Burrow.Server.MAPPER)

    init {
        clients.dropped
                .subscribe(this::handleClientDropped)
    }

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
            if (!isNameValid(it)) {
                sendNoSuchChannel(it, client)
            } else {
                handleValidJoin(it, client)
            }
        }
    }

    private fun sendNoSuchChannel(channelName: String, client: ClientTracker.ConnectedClient) {
        val noSuchChannelMessage = KaleNumerics.NOSUCHCHANNEL.Message(source = serverName.name, target = client.name, channel = channelName, content = "No such channel")
        clients.send.onNext(client to noSuchChannelMessage)
    }

    private fun handleValidJoin(channelName: String, client: ClientTracker.ConnectedClient) {
        val channel = getOrCreateChannel(channelName)
        channel.users += ChannelUser(client.prefix)

        val joinMessage = JoinMessage.Message(source = client.prefix, channels = listOf(channel.name))
        clients.send.onNext(client to joinMessage)

        // todo: split message when there's too many users
        // todo: permissions for users - @ etc as a prefix
        val users = channel.users.all.values.map { it.prefix.nick }
        val namReplyMessage = Rpl353Message.Message(source = serverName.name, target = client.name, visibility = CharacterCodes.EQUALS.toString(), channel = channel.name, names = users)
        val endOfNamesMessage = KaleNumerics.ENDOFNAMES.Message(source = serverName.name, target = client.name, channel = channelName, content = "End of /NAMES list")

        clients.send.onNext(client to namReplyMessage)
        clients.send.onNext(client to endOfNamesMessage)

        // todo: batch sending up?
        val otherUsers = (users.toSet() - client.name).mapNotNull { clients.lookUpClient(it) }
        otherUsers.forEach {
            clients.send.onNext(it to JoinMessage.Message(source = client.prefix, channels = listOf(channel.name)))
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
        clients.send.onNext(client to message)

        sendPartsToOtherChannelUsers(client.prefix, channel = channel)
    }

    private fun handleClientDropped(client: ClientTracker.ConnectedClient) {
        val channelsForUser = channelsForUser(client.name)

        channelsForUser.forEach { channel ->
            sendPartsToOtherChannelUsers(client.prefix, channel = channel)
        }
    }

    private fun sendPartsToOtherChannelUsers(client: Prefix, channel: Channel) {
        val users = channel.users.all.values.map { it.prefix.nick }
        val otherUsers = (users.toSet() - client.nick).mapNotNull { clients.lookUpClient(it) }
        otherUsers.forEach { user ->
            val message = PartMessage.Message(source = client, channels = listOf(channel.name))
            clients.send.onNext(user to message)
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