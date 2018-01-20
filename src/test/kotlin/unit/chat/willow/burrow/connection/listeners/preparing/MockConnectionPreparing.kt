package unit.chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import chat.willow.kale.core.message.IrcMessage
import io.reactivex.Observable
import io.reactivex.Observer

class MockConnectionPreparing: IConnectionPreparing {

    var didPrepare = false
    lateinit var spyInput: Observable<IConnectionListening.Read>
    lateinit var spyAccumulator: ILineAccumulator
    lateinit var spyAccepted: IConnectionListening.Accepted
    lateinit var spyTracked: Observer<ConnectionTracker.Tracked>
    lateinit var spyDrop: Observer<ConnectionId>
    lateinit var spyConnections: MutableMap<ConnectionId, BurrowConnection>
    lateinit var spySend: Observer<IrcMessage>

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator,
        connection: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>,
        send: Observer<IrcMessage>
    ) {
        didPrepare = true
        spyInput = input
        spyAccumulator = accumulator
        spyAccepted = connection
        spyTracked = tracked
        spyDrop = drop
        spyConnections = connections
        spySend = send
    }

}