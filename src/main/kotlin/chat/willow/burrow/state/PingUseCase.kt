package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage

interface IPingUseCase {

    fun track(client: ClientTracker.ConnectedClient)

}

class PingUseCase(private val connections: IConnectionTracker): IPingUseCase {

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(PingMessage.Command.Descriptor)
                .subscribe { handlePing(it, client) }
    }

    private fun handlePing(observable: KaleObservable<PingMessage.Command>, client: ClientTracker.ConnectedClient) {
        val message = PongMessage.Message(token = observable.message.token)
        connections.send(client.connection.id, message)
    }

}