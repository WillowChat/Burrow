package chat.willow.burrow.state

import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType

interface ClientUseCasing {

    fun track(client: ClientTracker.ConnectedClient)

}

class ClientUseCase(val connections: IConnectionTracker): ClientUseCasing {

    private val LOGGER = loggerFor<ClientUseCase>()

    private val channels = ChannelsUseCase(connections)
    private val ping = PingUseCase(connections)

    override fun track(client: ClientTracker.ConnectedClient) {
        connections.send(client.connection.id, Rpl001MessageType(source = "bunnies", target = client.prefix.nick, contents = "welcome to burrow"))

        ping.track(client)
        channels.track(client)

        LOGGER.info("tracked client ${client.connection.id}")
    }

}