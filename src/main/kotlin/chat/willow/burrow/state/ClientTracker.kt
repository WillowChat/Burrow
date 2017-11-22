package chat.willow.burrow.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.IMessageParser
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.message.IrcMessageParser
import chat.willow.kale.irc.message.rfc1459.NickMessage
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001Message
import chat.willow.kale.irc.message.rfc1459.rpl.Rpl001MessageType
import chat.willow.kale.irc.prefix.Prefix
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.rxkotlin.Observables
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.ConcurrentHashMap

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

        val regClient = RegisteringClient(connection)
        registeringClients += connection.id to regClient

        // todo: start a registration timer for the client

        val messages = connection.accumulator.lines
                .observeOn(lineScheduler)
                .map(IrcMessageParser::parse)
                .filterNotNull()
                .share()

        val users = messages.filter(UserMessage.command, UserMessage.Command.Parser)
        val nicks = messages.filter(NickMessage.command, NickMessage.Command.Parser)

        val userNicks = Observables.combineLatest(users, nicks)

        userNicks.take(1).subscribe {
            val user = it.first
            val nick = it.second

            // todo: verify stuff

            val client = ConnectedClient(connection, prefix = Prefix(nick = nick.nickname, user = user.username, host = connection.host))

            registeringClients -= connection.id
            connectedClients += connection.id to client

            connections.send(connection.id, Rpl001MessageType(source = "bunnies", target = client.prefix.nick, contents = "welcome to bunnies"))

            LOGGER.info("connection $connection registered: $it")
        }

        LOGGER.info("tracked registering client $connection")
        messages.subscribe { handle(connection, it) }
    }

    private fun <T:Any> Observable<IrcMessage>.filter(command: String, parser: IMessageParser<T>): Observable<T> {
        return this.filter { it.command.equals(command, ignoreCase = true) }
                .map(parser::parse)
                .filterNotNull()
    }

    private fun handle(connection: BurrowConnection, message: IrcMessage) {
        val id = connection.id

        val isRegistering = lazy { registeringClients.contains(connection.id) }
        val isConnected = lazy { connectedClients.contains(connection.id) }

        when {
            isRegistering.value -> {
                LOGGER.info("(registering) $id ~ >> $message")
            }

            isConnected.value -> {
                LOGGER.info("(connected) $id ~ >> $message")
            }

            else -> throw RuntimeException("handling message for a client that isn't tracked $connection $message")
        }
    }

    private fun drop(id: ConnectionId) {
        LOGGER.info("dropping $id")

        registeringClients -= id
        connectedClients -= id
    }

}

fun <T : Any> Observable<out T?>.filterNotNull(): Observable<T> {
    @Suppress("UNCHECKED_CAST")
    return this.filter { it != null } as Observable<T>
}