package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IKale
import chat.willow.kale.Kale
import chat.willow.kale.KaleMetadataFactory
import chat.willow.kale.KaleRouter
import chat.willow.kale.core.tag.KaleTagRouter
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.prefix.Prefix
import io.reactivex.Observer
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap

interface IKaleFactory {
    fun create(): IKale
}

object KaleFactory: IKaleFactory {
    override fun create(): IKale {
        return Kale(KaleRouter(), KaleMetadataFactory(KaleTagRouter()))
    }
}

interface IClientTracker {

    val track: Observer<BurrowConnection>
    val drop: Observer<ConnectionId>

}

interface IConnectionIdHaving {
    val connectionId: ConnectionId
}

class ClientTracker(val connections: IConnectionTracker,
                    val registrationUseCase: IRegistrationUseCase,
                    val clientsUseCase: IClientsUseCase,
                    val kaleFactory: IKaleFactory = KaleFactory,
                    val supportedCaps: Map<String, String?>): IClientTracker {

    private val LOGGER = loggerFor<ClientTracker>()

    data class RegisteringClient(val connection: BurrowConnection)
    private val registeringClients: MutableMap<ConnectionId, RegisteringClient> = ConcurrentHashMap()

    data class ConnectedClient(private val connection: BurrowConnection, val kale: IKale, val prefix: Prefix): INamed, IConnectionIdHaving {
        override val name: String
            get() = prefix.nick

        override val connectionId: ConnectionId
            get() = connection.id

        override fun toString(): String {
            return "ConnectedClient(id=$connectionId, prefix=$prefix)"
        }
    }
    private val connectedClients: MutableMap<ConnectionId, ConnectedClient> = ConcurrentHashMap()

    private val kales: MutableMap<ConnectionId, IKale> = ConcurrentHashMap()

    private val lineScheduler = Schedulers.single()

    override val track = PublishSubject.create<BurrowConnection>()
    override val drop = PublishSubject.create<ConnectionId>()

    init {
        track.subscribe(this::track)
        drop.subscribe(this::drop)
        drop.subscribe(clientsUseCase.drop)
    }

    private fun track(connection: BurrowConnection) {
        if (registeringClients.containsKey(connection.id) || connectedClients.containsKey(connection.id)) {
            throw RuntimeException("Tried to track connection $connection with duplicate ID")
        }

        registeringClients += connection.id to RegisteringClient(connection)

        val clientKale = kaleFactory.create()
        kales += connection.id to clientKale

        connections.read
            // todo: optimise?
            .filter { it.first == connection.id }
            .map { it.second }
            .observeOn(lineScheduler)
            .subscribe(clientKale.lines)

        clientKale.messages
            .map { "${connection.id} ~ >> ${it.message}" }
            .subscribe(LOGGER::debug)

        registrationUseCase
                .track(clientKale, supportedCaps, connection = connection)
                .takeUntil(connections.dropped.filter { it.id == connection.id })
                .subscribeBy(onNext = {
                    registered(connection, details = it, kale = clientKale)
                },
                onError = {
                    registrationFailed(connection, it)
                },
                onComplete = {
                    LOGGER.info("registration completed for connection ${connection.id}")
                })

        LOGGER.info("tracked registering client $connection")
    }

    private fun registrationFailed(connection: BurrowConnection, error: Throwable) {
        LOGGER.info("connection failed to register, dropping ${connection.id} $error")
        drop(connection.id)
        connections.drop.onNext(connection.id)
    }

    private fun registered(connection: BurrowConnection, details: RegistrationUseCase.Registered, kale: IKale) {
        val client = ConnectedClient(connection, kale = kale, prefix = details.prefix)

        registeringClients -= connection.id
        connectedClients += connection.id to client

        LOGGER.info("connection $connection registered: $details")

        clientsUseCase.track
            .onNext(client)
    }

    private fun drop(id: ConnectionId) {
        LOGGER.info("dropping $id")

        registeringClients -= id
        connectedClients -= id
        kales -= id
    }

}