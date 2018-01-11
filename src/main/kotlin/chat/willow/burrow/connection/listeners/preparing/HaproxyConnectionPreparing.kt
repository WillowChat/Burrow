package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.IHaproxyHeaderDecoder
import chat.willow.burrow.helper.loggerFor
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.subscribeBy

class HaproxyConnectionPreparing(
    private val factory: IBurrowConnectionFactory,
    private val decoder: IHaproxyHeaderDecoder
) :
    IConnectionPreparing {

    private val LOGGER = loggerFor<HaproxyConnectionPreparing>()

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator,
        connection: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>
    ) {
        accumulateAfterFirstInput(input, accumulator)

        LOGGER.info("Waiting for haproxy frame...")

        val maybeHaproxyFrame = decodeFirstInput(input)
        dropIfFrameFailedToDecode(maybeHaproxyFrame, drop, connection)

        val haproxyFrame = maybeHaproxyFrame.onErrorResumeNext(Observable.empty())

        val newConnection = makeBurrowConnection(haproxyFrame, connection).share()
        addNewConnection(newConnection, connections, connection)
        trackNewConnection(newConnection, tracked)

        replayInputFromFrame(newConnection, accumulator)
    }

    private fun makeBurrowConnection(
        haproxyFrame: Observable<HaproxyHeaderDecoder.Output>,
        connection: IConnectionListening.Accepted
    ): Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>> {
        return haproxyFrame
            .map {
                val primitiveConnection = connection.primitiveConnection
                primitiveConnection.host = it.header.sourceAddress.canonicalHostName

                factory.create(connection.id, primitiveConnection) to it
            }
    }

    private fun replayInputFromFrame(
        newConnection: Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>>,
        accumulator: ILineAccumulator
    ) {
        newConnection.subscribe {
            val frame = it.second
            if (frame.remainingBytesRead != 0
            ) {
                accumulator.input.onNext(
                    ILineAccumulator.Input(
                        bytes = frame.remainingBytes,
                        bytesRead = frame.remainingBytesRead
                    )
                )
            }
        }
    }

    private fun trackNewConnection(
        newConnection: Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>>,
        tracked: Observer<ConnectionTracker.Tracked>
    ) {
        newConnection
            .subscribe {
                tracked.onNext(ConnectionTracker.Tracked(it.first))
                LOGGER.info("Tracked connection ${it.first}")
            }
    }

    private fun addNewConnection(
        newConnection: Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>>,
        connections: MutableMap<ConnectionId, BurrowConnection>,
        connection: IConnectionListening.Accepted
    ) {
        newConnection
            .subscribe {
                val burrowConnection = it.first
                connections[connection.id] = burrowConnection
            }
    }

    private fun dropIfFrameFailedToDecode(
        haproxyFrame: Observable<HaproxyHeaderDecoder.Output>,
        drop: Observer<ConnectionId>,
        accepted: IConnectionListening.Accepted
    ) {
        haproxyFrame
            .subscribeBy(onError = {
                LOGGER.warn("Haproxy frame errored out", it)
                drop.onNext(accepted.id)
                accepted.primitiveConnection.close()
            })
    }

    private fun decodeFirstInput(input: Observable<IConnectionListening.Read>): Observable<HaproxyHeaderDecoder.Output> {
        return input
            .take(1)
            .map {
                HaproxyHeaderDecoder.Input(
                    bytes = it.buffer.array(),
                    bytesRead = it.bytesRead
                )
            }
            .map(decoder::decode)
    }

    private fun accumulateAfterFirstInput(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator
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
    }
}