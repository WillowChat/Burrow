package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
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
            .map { PongMessage.Message(token = it.message.token) }
            .subscribe { connections.send(client.connection.id, it) }

        connections.send(client.connection.id, Rpl001MessageType(source = "bunnies", target = client.prefix.nick, contents = "welcome to burrow"))

        LOGGER.info("tracked client ${client.connection.id}")
    }

}