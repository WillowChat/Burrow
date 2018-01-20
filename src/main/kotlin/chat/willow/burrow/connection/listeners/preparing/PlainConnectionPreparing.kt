package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.core.message.IrcMessage
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.schedulers.Schedulers

class PlainConnectionPreparing(
    private val factory: IBurrowConnectionFactory,
    private val hostnameLookupUseCase: IHostLookupUseCase,
    private val lookupScheduler: Scheduler = Schedulers.io()
) :
    IConnectionPreparing {

    private val LOGGER = loggerFor<PlainConnectionPreparing>()

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator,
        connection: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>,
        send: Observer<IrcMessage>
    ) {
        input
            .map {
                ILineAccumulator.Input(
                    bytes = it.bytes,
                    bytesRead = it.bytes.size
                )
            }
            .subscribe(accumulator.input::onNext)

        send.onNext(LOOKING_UP_MESSAGE)

        hostnameLookupUseCase.lookUp(connection.primitiveConnection.address, connection.primitiveConnection.host)
            .observeOn(lookupScheduler)
            .map {
                val primitiveConnection = connection.primitiveConnection
                val burrowConnection = factory.create(connection.id, primitiveConnection)

                connections[connection.id] = burrowConnection

                ConnectionTracker.Tracked(burrowConnection)
            }
            .subscribe(tracked::onNext)
    }

    companion object {
        val LOOKING_UP_MESSAGE = IrcMessage(prefix = "bunnies", command = "NOTICE", parameters = listOf("*", "Looking up your hostname before registration"))
    }
}