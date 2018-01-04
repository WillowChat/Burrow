package chat.willow.burrow.state

import chat.willow.kale.core.message.KaleObservable
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

interface IPingUseCase {

    fun track(client: ClientTracker.ConnectedClient)
    val timeout: Observer<ClientTracker.ConnectedClient>

}

class PingUseCase(private val clients: IClientsUseCase, private val timerScheduler: Scheduler = Schedulers.computation()): IPingUseCase {

    override val timeout = PublishSubject.create<ClientTracker.ConnectedClient>()

    private val PING_AFTER_SECONDS = 30L
    private val TIMEOUT_AFTER_SECONDS = 30L

    override fun track(client: ClientTracker.ConnectedClient) {
        client.kale
                .observe(PingMessage.Command.Descriptor)
                .subscribe { handlePing(it, client) }

        val clientDropped = clients.dropped.filter { it == client }

        val pongResponses = Observable.just(client)
                .delay(PING_AFTER_SECONDS, TimeUnit.SECONDS, timerScheduler)
                .flatMap { pingClient(client, token = "bunnies") }
                .repeat()
                .takeUntil(clientDropped)

        pongResponses
                .subscribeBy(
                        onError = {
                            timeout.onNext(client)
                        }
                )
    }

    private fun pingClient(client: ClientTracker.ConnectedClient, token: String): Observable<String> {
        clients.send.onNext(client to PingMessage.Command(token))

        return client.kale
                .observe(PongMessage.Message.Descriptor)
                .map { it.message.token }
                .filter { it == token }
                .timeout(TIMEOUT_AFTER_SECONDS, TimeUnit.SECONDS, timerScheduler)
                .take(1)
    }

    private fun handlePing(observable: KaleObservable<PingMessage.Command>, client: ClientTracker.ConnectedClient) {
        val message = PongMessage.Message(token = observable.message.token)
        clients.send.onNext(client to message)
    }

}