package chat.willow.burrow.unit.state

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.IConnectionTracker
import chat.willow.burrow.connection.line.ILineAccumulator
import chat.willow.burrow.connection.network.INetworkSocket
import chat.willow.burrow.state.RegistrationUseCase
import chat.willow.burrow.utility.mockKaleObservable
import chat.willow.kale.IKale
import chat.willow.kale.KaleDescriptor
import chat.willow.kale.KaleObservable
import chat.willow.kale.irc.message.extension.cap.CapMessage
import chat.willow.kale.irc.message.rfc1459.NickMessage
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.prefix.Prefix
import chat.willow.kale.irc.tag.TagStore
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.schedulers.TestScheduler
import io.reactivex.subjects.PublishSubject
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RegistrationUseCaseTests {

    private lateinit var sut: RegistrationUseCase
    private lateinit var mockConnectionTracker: IConnectionTracker

    private lateinit var mockSocket: INetworkSocket
    private lateinit var mockAccumulator: ILineAccumulator

    private lateinit var mockKale: IKale

    private lateinit var mockUser: PublishSubject<UserMessage.Command>
    private lateinit var mockNick: PublishSubject<NickMessage.Command>
    private lateinit var mockCapEnd: PublishSubject<CapMessage.End.Command>
    private lateinit var mockCapLs: PublishSubject<CapMessage.Ls.Command>
    private lateinit var mockCapReq: PublishSubject<CapMessage.Req.Command>

    private lateinit var scheduler: TestScheduler

    private lateinit var connection: BurrowConnection

    @Before fun setUp() {
        mockSocket = mock()
        mockAccumulator = mock()
        mockConnectionTracker = mock()

        mockKale = mock()
        connection = BurrowConnection(id = 0, host = "host", socket = mockSocket, accumulator = mockAccumulator)

        mockUser = mockKaleObservable(mockKale, UserMessage.Command.Descriptor)
        mockNick = mockKaleObservable(mockKale, NickMessage.Command.Descriptor)
        mockCapEnd = mockKaleObservable(mockKale, CapMessage.End.Command.Descriptor)
        mockCapLs = mockKaleObservable(mockKale, CapMessage.Ls.Command.Descriptor)
        mockCapReq = mockKaleObservable(mockKale, CapMessage.Req.Command.Descriptor)

        scheduler = TestScheduler()

        sut = RegistrationUseCase(mockConnectionTracker, scheduler)
    }

    @Test fun `single USER and NICK results in registration without caps`() {
        val observer = sut.track(mockKale, mapOf(), connection).test()

        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf()))
    }

    @Test fun `double USER and NICK results in a single registration`() {
        val observer = sut.track(mockKale, mapOf(), connection).test()

        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf()))
    }

    @Test fun `USER, but no NICK, results in a timeout after 5 seconds`() {
        val observer = sut.track(mockKale, mapOf(), connection).test()

        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        scheduler.advanceTimeTo(5, TimeUnit.SECONDS)

        observer.assertError(TimeoutException::class.java)
    }

    @Test fun `CAP LS, then USER and NICK, results in a timeout`() {
        val observer = sut.track(mockKale, mapOf(), connection).test()

        mockCapLs.onNext(CapMessage.Ls.Command(version = "302"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        scheduler.advanceTimeTo(5, TimeUnit.SECONDS)

        observer.assertError(TimeoutException::class.java)
    }

    @Test fun `CAP LS, USER, NICK, and CAP END results in no caps enabled`() {
        val observer = sut.track(mockKale, mapOf(), connection).test()

        mockCapLs.onNext(CapMessage.Ls.Command(version = "302"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockCapEnd.onNext(CapMessage.End.Command)

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf()))
    }

    @Test fun `ircv3 negotiation with caps we support results in those caps being enabled`() {
        val observer = sut.track(mockKale, caps = mapOf("someKey" to "someValue"), connection = connection).test()

        mockCapLs.onNext(CapMessage.Ls.Command(version = "302"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockCapReq.onNext(CapMessage.Req.Command(caps = listOf("someKey")))
        mockCapEnd.onNext(CapMessage.End.Command)

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf("someKey")))
    }

    @Test fun `ircv3 negotiation with caps the client doesn't support results in those caps being disabled`() {
        val observer = sut.track(mockKale, caps = mapOf("supportedKey" to null, "unsupportedKey" to null), connection = connection).test()

        mockCapLs.onNext(CapMessage.Ls.Command(version = "302"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockCapReq.onNext(CapMessage.Req.Command(caps = listOf("supportedKey")))
        mockCapEnd.onNext(CapMessage.End.Command)

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf("supportedKey")))
    }

    @Test fun `ircv3 negotiation with client requesting CAPs we don't support results in no caps enabled`() {
        val observer = sut.track(mockKale, caps = mapOf("supportedKey" to null, "unsupportedKey" to null), connection = connection).test()

        mockCapLs.onNext(CapMessage.Ls.Command(version = "302"))
        mockUser.onNext(UserMessage.Command("username", "*", "realname"))
        mockNick.onNext(NickMessage.Command("nickname"))
        mockCapReq.onNext(CapMessage.Req.Command(caps = listOf("keyOnlyClientSupports")))
        mockCapEnd.onNext(CapMessage.End.Command)

        observer.assertValue(RegistrationUseCase.Registered(Prefix(nick = "nickname", user = "username", host = "host"), caps = setOf()))
    }

}

fun <T> message(message: T): KaleObservable<T> {
    return KaleObservable(message, TagStore())
}