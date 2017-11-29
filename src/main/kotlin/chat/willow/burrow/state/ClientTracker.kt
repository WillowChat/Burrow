package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IKale
import chat.willow.kale.Kale
import chat.willow.kale.KaleMetadataFactory
import chat.willow.kale.KaleRouter
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.tag.KaleTagRouter
import io.reactivex.Observer
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

interface IClientTracker {

    val track: Observer<BurrowConnection>
    val drop: Observer<ConnectionId>

}

class ClientTracker(val connections: IConnectionTracker): IClientTracker {

    private val LOGGER = loggerFor<ClientTracker>()

    data class RegisteringClient(val connection: BurrowConnection)
    private val registeringClients: MutableMap<ConnectionId, RegisteringClient> = ConcurrentHashMap()

    data class ConnectedClient(val connection: BurrowConnection, val prefix: Prefix)
    private val connectedClients: MutableMap<ConnectionId, ConnectedClient> = ConcurrentHashMap()

    private val kales: MutableMap<ConnectionId, IKale> = ConcurrentHashMap()

    private val lineScheduler = Schedulers.single()

    override val track = PublishSubject.create<BurrowConnection>()
    override val drop = PublishSubject.create<ConnectionId>()

    init {
        track.subscribe(this::track)
        drop.subscribe(this::drop)
    }

    private fun track(connection: BurrowConnection) {
        if (registeringClients.containsKey(connection.id) || connectedClients.containsKey(connection.id)) {
            throw RuntimeException("Tried to track connection $connection with duplicate ID")
        }

        registeringClients += connection.id to RegisteringClient(connection)

        val clientKale = Kale(KaleRouter(), KaleMetadataFactory(KaleTagRouter()))
        kales += connection.id to clientKale

        connection.accumulator.lines
                .observeOn(lineScheduler)
                .subscribe(clientKale.lines)

        clientKale.messages.subscribe { LOGGER.info("${connection.id} ~ >> ${it.message}")}

        RegistrationUseCase(connections, connection)
                .track(clientKale, caps = mapOf("cap-notify" to null))
                .subscribeBy(onNext = {
                    registered(connection, it)
                },
                onError = {
                    registrationFailed(connection, it)
                },
                onComplete = {
                    LOGGER.info("registration completed for connection ${connection.id}")
                })

        clientKale.observe(PingMessage.Command.Descriptor)
                .throttleFirst(5, TimeUnit.SECONDS, Schedulers.trampoline())
                .subscribe { connections.send(connection.id, PongMessage.Message(token = it.message.token)) }

        LOGGER.info("tracked registering client $connection")
    }

    private fun registrationFailed(connection: BurrowConnection, error: Throwable) {
        LOGGER.info("connection failed to register, dropping ${connection.id} $error")
        drop(connection.id)

        connection.socket.close()
    }

    private fun registered(connection: BurrowConnection, details: RegistrationUseCase.Registered) {
        val client = ConnectedClient(connection, prefix = details.prefix)

        registeringClients -= connection.id
        connectedClients += connection.id to client

        connections.send(connection.id, Rpl001MessageType(source = "bunnies", target = client.prefix.nick, contents = "welcome to bunnies"))

        LOGGER.info("connection $connection registered: $details")
    }

    private fun drop(id: ConnectionId) {
        LOGGER.info("dropping $id")

        registeringClients -= id
        connectedClients -= id
        kales -= id
    }

}