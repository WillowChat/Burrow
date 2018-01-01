package unit.chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.listeners.preparing.IConnectionPreparing
import io.reactivex.Observable
import io.reactivex.Observer

class MockConnectionPreparing: IConnectionPreparing {

    var didPrepare = false
    lateinit var spyInput: Observable<IConnectionListening.Read>
    lateinit var spyAccumulator: LineAccumulator
    lateinit var spyAccepted: IConnectionListening.Accepted
    lateinit var spyTracked: Observer<ConnectionTracker.Tracked>
    lateinit var spyDrop: Observer<ConnectionId>
    lateinit var spyConnections: MutableMap<ConnectionId, BurrowConnection>
    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: LineAccumulator,
        accepted: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>
    ) {
        didPrepare = true
        spyInput = input
        spyAccumulator = accumulator
        spyAccepted = accepted
        spyTracked = tracked
        spyDrop = drop
        spyConnections = connections
    }

}