package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.kale.KaleObservable
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.helper.CaseMapping
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.message.rfc1459.JoinMessage

interface IChannelsUseCase {

    val channels: CaseInsensitiveNamedMap<Channel>
    fun track(client: ClientTracker.ConnectedClient)

}

data class Channel(override val name: String): INamed

class ChannelsUseCase(private val connections: IConnectionTracker): IChannelsUseCase {

    override val channels = CaseInsensitiveNamedMap<Channel>(mapper = Burrow.Server.MAPPER)

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(JoinMessage.Command.Descriptor)
                .subscribe { handleJoin(it, client) }
    }

    private fun handleJoin(observable: KaleObservable<JoinMessage.Command>, client: ClientTracker.ConnectedClient) {
        observable.message.channels.forEach {
            val channelExists = channels.contains(it)

            if (channelExists) {
                // todo: put user in the channel
            } else {
                channels += Channel(name = it)
            }

            val message = JoinMessage.Message(source = client.prefix, channels = observable.message.channels)
            connections.send(client.connection.id, message)
        }
    }

}