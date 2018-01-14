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
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timer

class HaproxyConnectionPreparing(
    private val factory: IBurrowConnectionFactory,
    private val decoder: IHaproxyHeaderDecoder,
    private val hostnameLookupUseCase: IHostLookupUseCase
) :
    IConnectionPreparing {

    private val LOGGER = loggerFor<HaproxyConnectionPreparing>()
    private val timerScheduler = Schedulers.computation()
    private val fakeHostnameLookupScheduler = Schedulers.io()

    private val HOSTNAME_LOOKUP_TIMEOUT_SECONDS: Long = 10

    override fun prepare(
        input: Observable<IConnectionListening.Read>,
        accumulator: ILineAccumulator,
        connection: IConnectionListening.Accepted,
        tracked: Observer<ConnectionTracker.Tracked>,
        drop: Observer<ConnectionId>,
        connections: MutableMap<ConnectionId, BurrowConnection>
    ) {
        accumulateAfterFirstInput(input, accumulator)

        LOGGER.info("${connection.id} - Waiting for haproxy frame...")

        val maybeHaproxyFrame = decodeFirstInput(input)
        dropIfFrameFailedToDecode(maybeHaproxyFrame, drop, connection)

        val haproxyFrame = maybeHaproxyFrame.onErrorResumeNext(Observable.empty()).share()

        val newConnection = makeBurrowConnection(haproxyFrame, connection)
            .share()

        addNewConnection(newConnection, connections, connection)
        trackNewConnection(newConnection, tracked)

        replayInputFromFrame(newConnection, accumulator)
    }

    private fun makeBurrowConnection(
        haproxyFrame: Observable<HaproxyHeaderDecoder.Output>,
        connection: IConnectionListening.Accepted
    ): Observable<Pair<BurrowConnection, HaproxyHeaderDecoder.Output>> {
        val hostnameLookup = haproxyFrame
            .flatMap { hostnameLookupUseCase.lookUp(it.header.sourceAddress, default = it.header.sourceAddress.hostAddress)}

        return Observables.combineLatest(haproxyFrame, hostnameLookup) { frame, hostname -> (frame to hostname) }
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