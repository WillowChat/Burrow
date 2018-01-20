package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.kale.core.message.IrcMessage
import io.reactivex.Observable
import io.reactivex.Observer

interface IConnectionPreparing {
    fun prepare(input: Observable<IConnectionListening.Read>,
                accumulator: ILineAccumulator,
                connection: IConnectionListening.Accepted,
                tracked: Observer<ConnectionTracker.Tracked>,
                drop: Observer<ConnectionId>,
                connections: MutableMap<ConnectionId, BurrowConnection>,
                send: Observer<IrcMessage>
    )
}

