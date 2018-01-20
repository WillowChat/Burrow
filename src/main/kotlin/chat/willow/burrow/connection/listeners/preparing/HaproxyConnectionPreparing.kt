package chat.willow.burrow.connection.listeners.preparing

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.ConnectionTracker
import chat.willow.burrow.connection.IBurrowConnectionFactory
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.listeners.IConnectionListening
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.IHaproxyHeaderDecoder
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.core.message.IrcMessage
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

class HaproxyConnectionPreparing(
    private val factory: IBurrowConnectionFactory,
    private val decoder: IHaproxyHeaderDecoder,
    private val hostnameLookupUseCase: IHostLookupUseCase,
    private val lookupScheduler: Scheduler = Schedulers.io()
) :
    IConnectionPreparing {

    private val LOGGER = loggerFor<HaproxyConnectionPreparing>()

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator,
        connection: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>,
        send: Observer<IrcMessage>
    ) {
        accumulateAfterFirstInput(input, accumulator)

        LOGGER.info("${connection.id} - Waiting for haproxy frame...")

        val maybeHaproxyFrame = decodeFirstInput(input)
        dropIfFrameFailedToDecode(maybeHaproxyFrame, drop, connection)

        val haproxyFrame = maybeHaproxyFrame.onErrorResumeNext(Observable.empty()).share()

        val newConnection = makeBurrowConnection(haproxyFrame, connection, send)
            .share()

        addNewConnection(newConnection, connections, connection)
        trackNewConnection(newConnection, tracked)

        replayInputFromFrame(newConnection, accumulator)
    }

    private fun makeBurrowConnection(
        haproxyFrame: Observable<HaproxyHeaderDecoder.Output>,
        connection: IConnectionListening.Accepted,
        send: Observer<IrcMessage>
    ): Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>> {
        haproxyFrame
            .take(1)
            .map { PlainConnectionPreparing.LOOKING_UP_MESSAGE }
            .subscribe(send::onNext)

        val hostnameLookup = haproxyFrame
            .observeOn(lookupScheduler)
            .flatMap({
                hostnameLookupUseCase.lookUp(it.header.sourceAddress)
                    .onErrorResumeNext { error: Throwable ->
                        val errorMessage = when (error) {
                            HostLookupUseCase.ForwardLookupNotFound -> "Forward lookup verification failed"
                            else -> "Unknown exception: ${error::class}"
                        }
                        send.onNext(PlainConnectionPreparing.LOOK_UP_FAILED_MESSAGE(errorMessage))
                        Observable.just(it.header.sourceAddress.hostAddress)
                    }
            }, { frame, lookup -> frame to lookup })
            .share()

        hostnameLookup
            .map { PlainConnectionPreparing.LOOK_UP_COMPLETE(it.second) }
            .subscribe(send::onNext)

        return hostnameLookup
            .map { (frame, hostname) ->
                val primitiveConnection = connection.primitiveConnection
                primitiveConnection.host = hostname

                factory.create(connection.id, primitiveConnection) to frame
            }
    }

    private fun replayInputFromFrame(
        newConnection: Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>>,
        accumulator: ILineAccumulator
    ) {
        newConnection.flatMap {
            val frame = it.second
            if (frame.remainingBytesRead != 0) {
                Observable.just(
                    ILineAccumulator.Input(
                        bytes = frame.remainingBytes,
                        bytesRead = frame.remainingBytesRead
                    )
                )
            } else {
                Observable.empty()
            }
        }
        .subscribe(accumulator.input::onNext)
    }

    private fun trackNewConnection(
        newConnection: Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>>,
        tracked: Observer<ConnectionTracker.Tracked>
    ) {
        newConnection
            .map { ConnectionTracker.Tracked(it.first) }
            .subscribe(tracked::onNext)
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
                    bytes = it.bytes,
                    bytesRead = it.bytes.size
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
                    bytes = it.bytes,
                    bytesRead = it.bytes.size
                )
            }
            .subscribe(accumulator.input::onNext)
    }
}