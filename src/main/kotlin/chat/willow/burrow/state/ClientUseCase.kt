package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.rfc1459.JoinMessage
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType

interface ClientUseCasing {

    fun track(client: ClientTracker.ConnectedClient)

}

class ClientUseCase(val connections: IConnectionTracker): ClientUseCasing {

    private val LOGGER = loggerFor<ClientUseCase>()

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(PingMessage.Command.Descriptor)
                .subscribe { handlePing(it, client) }

        client.kale
                .observe(JoinMessage.Command.Descriptor)
                .subscribe { handleJoin(it, client) }

        connections.send(client.connection.id, Rpl001MessageType(source = "bunnies", target = client.prefix.nick, contents = "welcome to burrow"))

        LOGGER.info("tracked client ${client.connection.id}")
    }

    private fun handlePing(observable: KaleObservable<PingMessage.Command>, client: ClientTracker.ConnectedClient) {
        val message = PongMessage.Message(token = observable.message.token)
        connections.send(client.connection.id, message)
    }

    private fun handleJoin(observable: KaleObservable<JoinMessage.Command>, client: ClientTracker.ConnectedClient) {
        val message = JoinMessage.Message(source = client.prefix, channels = observable.message.channels)
        connections.send(client.connection.id, message)
    }

}