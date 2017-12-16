package chat.willow.burrow.state

import chat.willow.burrow.Burrow
import chat.willow.burrow.Burrow.Validation.alphanumeric
import chat.willow.burrow.Burrow.Validation.nick
import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.ICommand
import chat.willow.kale.IKale
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.NickMessage
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.message.rfc1459.rpl.RplSourceTargetChannelContent
import chat.willow.kale.irc.message.rfc1459.rpl.RplSourceTargetContent
import chat.willow.kale.irc.prefix.Prefix
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

interface IRegistrationUseCase {

    fun track(kale: IKale, caps: Map<String, String?> = mapOf(), connection: BurrowConnection): Observable<RegistrationUseCase.Registered>

}

// todo: move in to Kale

object Rpl433Message : ICommand {

    override val command = "433"

    class Message(source: String, target: String, content: String): RplSourceTargetContent.Message(source, target, content)
    object Parser : RplSourceTargetContent.Parser(command)
    object Serialiser : RplSourceTargetContent.Serialiser(command)
    object Descriptor : RplSourceTargetContent.Descriptor(command, Parser)

}

class RegistrationUseCase(private val connections: IConnectionTracker, private val clients: IClientsUseCase, private val scheduler: Scheduler = Schedulers.computation()): IRegistrationUseCase {

    private val LOGGER = loggerFor<RegistrationUseCase>()

    data class Registered(val prefix: Prefix, val caps: Set<String>)

    private val MAX_USER_LENGTH = 9
    private val MAX_NICK_LENGTH = 30 // todo: isupport
    private val TIMEOUT_SECONDS: Long = 5

    override fun track(kale: IKale, caps: Map<String, String?>, connection: BurrowConnection): Observable<Registered> {
        val users = kale.observe(UserMessage.Command.Descriptor).share()
        val nicks = kale.observe(NickMessage.Command.Descriptor).share()

        val validatedUsers = users.map { it.message.username to validateUser(it.message.username) }
                .filter { it.second }
                .map { it.first }

        val validatedNicks = nicks.map { it.message.nickname to validateNick(it.message.nickname) }
                .filter { it.second }
                .map { it.first }

        val existenceCheckedNicks = validatedNicks
                .map { it to isNickTaken(it) }
                .share()

        existenceCheckedNicks
                .filter { it.second }
                .map { it.first to connection }
                .subscribe(this::sendAlreadyExists)

        val nonExistentNicks = existenceCheckedNicks
                .filter { !it.second }
                .map { it.first }

        val capEnd = kale.observe(CapMessage.End.Command.Descriptor).share()
        val capLs = kale.observe(CapMessage.Ls.Command.Descriptor).share()
        val capReq = kale.observe(CapMessage.Req.Command.Descriptor).share()

        capLs
                .map { CapMessage.Ls.Message(target = "*", caps = caps, isMultiline = false) }
                .subscribe { connections.send(connection.id, it) }

        val requestedSupportedCaps = capReq.flatMap {
            val requestedCaps = it.message.caps.toSet()
            val allCapsSupported = caps.keys.containsAll(requestedCaps)
            return@flatMap if (allCapsSupported) {
                Observable.just(requestedCaps)
            } else {
                Observable.empty()
            }
        }.share()

        val requestedUnsupportedCaps = capReq.flatMap {
            val requestedCaps = it.message.caps.toSet()
            val allCapsSupported = caps.keys.containsAll(requestedCaps)
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
                .share()

        val userAndNick = Observables.combineLatest(validatedUsers, nonExistentNicks).share()

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

        return Observable.merge(rfc1459Registration, ircv3Registration)
                .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS, scheduler)
                .take(1)
    }

    private fun validateUser(user: String): Boolean = !user.isEmpty() && user.length <= MAX_USER_LENGTH && nick.test(user)

    private fun validateNick(nick: String): Boolean = !nick.isEmpty() && nick.length <= MAX_NICK_LENGTH && Burrow.Validation.nick.test(nick)

    private fun isNickTaken(nick: String): Boolean {
        return clients.lookUpClient(nick) != null
    }

    private fun sendAlreadyExists(nickAndConnection: Pair<String, BurrowConnection>) {
        val (nick, connection) = nickAndConnection
        val message = Rpl433Message.Message(source = "bunnies", target = nick, content = "Nickname is already in use")
        connections.send(connection.id, message)
    }

}