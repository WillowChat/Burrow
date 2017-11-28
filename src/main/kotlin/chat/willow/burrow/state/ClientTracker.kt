package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.*
import chat.willow.kale.irc.message.IMessageParser
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.message.IrcMessageParser
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.NickMessage
import chat.willow.kale.irc.message.rfc1459.PingMessage
import chat.willow.kale.irc.message.rfc1459.PongMessage
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.tag.KaleTagRouter
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface IClientTracker {

    val track: Observer<BurrowConnection>
    val drop: Observer<ConnectionId>

}

class RegistrationUseCase(private val connections: IConnectionTracker, private val connection: BurrowConnection, kale: IKale, timeoutMs: Long) {

    private val LOGGER = loggerFor<RegistrationUseCase>()

    val registered: Observable<Registered>
    private val registeredSubject = PublishSubject.create<Registered>()
    data class Registered(val prefix: Prefix, val caps: Set<String>)

    private val MAX_USER_LENGTH = 9
    private val MAX_NICK_LENGTH = 30 // todo: isupport
    private val alphanumeric = Pattern.compile("^[a-zA-Z0-9]*$").asPredicate()

    init {
        registered = registeredSubject

        val users = kale.observe(UserMessage.Command.Descriptor)
        val nicks = kale.observe(NickMessage.Command.Descriptor)

        val validatedUsers = users.map { it.message.username to validateUser(it.message.username) }
                .filter { it.second }
                .map { it.first }

        val validatedNicks = nicks.map { it.message.nickname to validateNick(it.message.nickname) }
                .filter { it.second }
                .map { it.first }

        val capEnd = kale.observe(CapMessage.End.Command.Descriptor)
        val capLs = kale.observe(CapMessage.Ls.Command.Descriptor)
        val capReq = kale.observe(CapMessage.Req.Command.Descriptor)

        // todo: move
        val supportedCaps = mapOf("something" to "bunnies", "account-tag" to null, "cap-notify" to null)

        capLs
                .map { CapMessage.Ls.Message(target = "*", caps = supportedCaps, isMultiline = false) }
                .subscribe { connections.send(connection.id, it) }

        val requestedSupportedCaps = capReq.flatMap {
            val requestedCaps = it.message.caps.toSet()
            val allCapsSupported = supportedCaps.keys.containsAll(requestedCaps)
            return@flatMap if (allCapsSupported) {
                Observable.just(requestedCaps)
            } else {
                Observable.empty()
            }
        }

        val requestedUnsupportedCaps = capReq.flatMap {
            val requestedCaps = it.message.caps.toSet()
            val allCapsSupported = supportedCaps.keys.containsAll(requestedCaps)
            return@flatMap if (allCapsSupported) {
                Observable.empty()
            } else {
                Observable.just(requestedCaps)
            }
        }

        requestedSupportedCaps
                .subscribe { connections.send(connection.id, CapMessage.Ack.Message(target = "*", caps = it.toList())) }

        requestedUnsupportedCaps
                .subscribe { connections.send(connection.id, CapMessage.Nak.Message(target = "*", caps = it.toList())) }

        val negotiatedCaps = requestedSupportedCaps
                .scan(setOf<String>(), { initial, addition -> initial + addition })

        val startedNegotiatingCaps = Observable.merge(capLs, capReq)
                .map { true }

        val userAndNick = Observables.combineLatest(validatedUsers, validatedNicks)

        val rfc1459Registration = userAndNick
                .takeUntil(startedNegotiatingCaps)
                .map {
                    val user = it.first
                    val nick = it.second

                    Registered(prefix = Prefix(nick = nick, user = user, host = connection.host), caps = setOf())
                }

        val ircv3Registration = capEnd
                .withLatestFrom(startedNegotiatingCaps) { _, _ -> Unit }
                .withLatestFrom(Observables.combineLatest(negotiatedCaps, userAndNick)) { _, capsAndUserNicks -> capsAndUserNicks }
                .map {
                    val negotiated = it.first
                    val user = it.second.first
                    val nick = it.second.second

                    Registered(prefix = Prefix(nick = nick, user = user, host = connection.host), caps = negotiated)
                }

        Observable.merge(rfc1459Registration, ircv3Registration)
                .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                .take(1)
                .subscribe(registeredSubject)

    }

    private fun validateUser(user: String): Boolean = !user.isEmpty() && user.length <= MAX_USER_LENGTH && alphanumeric.test(user)

    private fun validateNick(nick: String): Boolean = !nick.isEmpty() && nick.length <= MAX_NICK_LENGTH && alphanumeric.test(nick)

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

        RegistrationUseCase(connections, connection, clientKale, timeoutMs = 5 * 1000)
                .registered
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