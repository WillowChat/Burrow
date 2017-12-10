package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.rfc1459.JoinMessage

interface IChannelsUseCase {

    fun track(client: ClientTracker.ConnectedClient)

}

class ChannelsUseCase(private val connections: IConnectionTracker): IChannelsUseCase {

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(JoinMessage.Command.Descriptor)
                .subscribe { handleJoin(it, client) }
    }

    private fun handleJoin(observable: KaleObservable<JoinMessage.Command>, client: ClientTracker.ConnectedClient) {
        val message = JoinMessage.Message(source = client.prefix, channels = observable.message.channels)
        connections.send(client.connection.id, message)
    }

}