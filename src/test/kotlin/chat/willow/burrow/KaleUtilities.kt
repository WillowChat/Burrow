package chat.willow.burrow

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.kale.IKale
import chat.willow.kale.irc.prefix.Prefix
import com.nhaarman.mockito_kotlin.mock

fun makeClient(kale: IKale, id: ConnectionId = 1, prefix: Prefix = chat.willow.kale.irc.prefix.prefix("someone")): ClientTracker.ConnectedClient {
    val accumulator = LineAccumulator(bufferSize = 1)
    val connection = BurrowConnection(id = id, host = prefix.host ?: "", socket = mock(), accumulator = accumulator)

    return ClientTracker.ConnectedClient(connection, kale, chat.willow.kale.irc.prefix.prefix("someone"))
}