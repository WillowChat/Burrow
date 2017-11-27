package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.IKale
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
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

interface IClientTracker {

    val track: Observer<BurrowConnection>
    val drop: Observer<ConnectionId>

}

class RegistrationUseCase(private val connection: BurrowConnection, kale: IKale, timeoutMs: Long) {

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

        // cap req or cap ls starter means they're starting cap negotiation
        kale.observe(CapMessage.Ls.Command.Descriptor)
                .subscribe { LOGGER.info("$it")}

        val userNicks = Observables.combineLatest(users, nicks)

        userNicks
                .timeout(timeoutMs, TimeUnit.MILLISECONDS)
                .map {
                    val user = it.first
                    val nick = it.second

                    Registered(prefix = Prefix(nick = nick.message.nickname, user = user.message.username, host = connection.host), caps = setOf())
                }
                .take(1)
                .subscribe(registeredSubject)
    }

    private fun validateUser(user: String): Boolean = !user.isEmpty() && user.length <= MAX_USER_LENGTH && alphanumeric.test(user)

    private fun validateNick(nick: String): Boolean = !nick.isEmpty() && nick.length <= MAX_NICK_LENGTH && alphanumeric.test(nick)

}

class ClientTracker(val connections: IConnectionTracker, val kale: IKale): IClientTracker {

    private val LOGGER = loggerFor<ClientTracker>()

    data class RegisteringClient(val connection: BurrowConnection)
    private val registeringClients: MutableMap<ConnectionId, RegisteringClient> = ConcurrentHashMap()

    data class ConnectedClient(val connection: BurrowConnection, val prefix: Prefix)
    private val connectedClients: MutableMap<ConnectionId, ConnectedClient> = ConcurrentHashMap()

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

        connection.accumulator.lines
                .observeOn(lineScheduler)
                .subscribe(kale.lines)

        RegistrationUseCase(connection, kale, timeoutMs = 5 * 1000)
                .registered
                .subscribeBy(onNext = {
                    registered(connection, it)
                },
                onError = {
                    registrationFailed(connection, it)
                })

        kale.observe(PingMessage.Command.Descriptor)
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
    }

}

private fun <T:Any> Observable<IrcMessage>.filter(command: String, parser: IMessageParser<T>): Observable<T> {
    return this.filter { it.command.equals(command, ignoreCase = true) }
            .map(parser::parse)
            .filterNotNull()
}

fun <T : Any> Observable<out T?>.filterNotNull(): Observable<T> {
    @Suppress("UNCHECKED_CAST")
    return this.filter { it != null } as Observable<T>
}