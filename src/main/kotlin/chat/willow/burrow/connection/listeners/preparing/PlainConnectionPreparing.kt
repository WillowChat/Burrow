package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Observer

class PlainConnectionPreparing(private val factory: IBurrowConnectionFactory) :
    IConnectionPreparing {

    private val LOGGER = loggerFor<PlainConnectionPreparing>()

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: LineAccumulator,
        accepted: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>
    ) {
        input
            .map {
                ILineAccumulator.Input(
                    bytes = it.buffer.array(),
                    bytesRead = it.bytesRead
                )
            }
            .subscribe(accumulator.input)

        val primitiveConnection = accepted.primitiveConnection
        val connection = factory.create(accepted.id, primitiveConnection)

        connections[connection.id] = connection

        LOGGER.info("Tracked connection $connection")
        tracked.onNext(ConnectionTracker.Tracked(connection))
    }
}