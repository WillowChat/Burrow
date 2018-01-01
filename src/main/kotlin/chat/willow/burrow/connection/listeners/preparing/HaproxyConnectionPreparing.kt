package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.subscribeBy

class HaproxyConnectionPreparing(private val factory: IBurrowConnectionFactory) :
    IConnectionPreparing {

    private val LOGGER =
        loggerFor<HaproxyConnectionPreparing>()

    private val haproxyHeaderDecoder = HaproxyHeaderDecoder()

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: LineAccumulator,
        accepted: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>
    ) {
        input
            .skip(1)
            .map {
                ILineAccumulator.Input(
                    bytes = it.buffer.array(),
                    bytesRead = it.bytesRead
                )
            }
            .subscribe(accumulator.input)

        LOGGER.info("Waiting for haproxy frame...")

        val haproxyFrame = input
            .take(1)
            .map {
                HaproxyHeaderDecoder.Input(
                    bytes = it.buffer.array(),
                    bytesRead = it.bytesRead
                )
            }
            .map(haproxyHeaderDecoder::decode)
            .share()

        haproxyFrame
            .subscribeBy(onError = {
                LOGGER.info("Haproxy frame errored out: $it")
                drop.onNext(accepted.id)
                accepted.primitiveConnection.close()
            })

        val newConnection = haproxyFrame
            .map {
                val primitiveConnection = accepted.primitiveConnection
                primitiveConnection.host = it.header.sourceAddress

                factory.create(accepted.id, primitiveConnection) to it
            }
            .share()

        newConnection
            .subscribe {
                val connection = it.first
                connections[connection.id] = connection
            }

        newConnection
            .subscribe {
                tracked.onNext(ConnectionTracker.Tracked(it.first))
                LOGGER.info("Tracked connection ${it.first}")

                val frame = it.second
                if (frame.remainingBytesRead != 0) {
                    accumulator.input.onNext(
                        ILineAccumulator.Input(
                            bytes = frame.remainingBytes,
                            bytesRead = frame.remainingBytesRead
                        )
                    )
                }
            }
    }
}