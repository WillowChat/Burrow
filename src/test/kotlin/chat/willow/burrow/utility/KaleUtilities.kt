package chat.willow.burrow.utility

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.burrow.unit.state.message
import chat.willow.kale.IKale
import chat.willow.kale.KaleDescriptor
import chat.willow.kale.irc.prefix.Prefix
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject


data class TestClient(val kale: IKale, val client: ClientTracker.ConnectedClient) {
    fun <T> mock(descriptor: KaleDescriptor<T>): PublishSubject<T> {
        return mockKaleObservable(kale, descriptor)
    }

    val prefix: Prefix
        get() = client.prefix

    val id: ConnectionId
        get() = client.connection.id

}

fun makeClient(kale: IKale = KaleUtilities.mockKale(), id: ConnectionId = 1, prefix: Prefix = chat.willow.kale.irc.prefix.prefix("someone")): TestClient {
    val accumulator = LineAccumulator(bufferSize = 1)
    val connection = BurrowConnection(id = id, host = prefix.host ?: "", socket = mock(), accumulator = accumulator)

    return TestClient(kale = kale, client = ClientTracker.ConnectedClient(connection, kale, prefix))
}

object KaleUtilities {

    fun mockKale(): IKale {
        val mockKale = mock<IKale>()
        whenever(mockKale.observe(any<KaleDescriptor<*>>())).thenReturn(PublishSubject.create())
        return mockKale
    }

}

fun <T> mockKaleObservable(kale: IKale, descriptor: KaleDescriptor<T>): PublishSubject<T> {
    val messageSubject = PublishSubject.create<T>()
    val kaleObservableSubject = messageSubject.map { message(it) }
    whenever(kale.observe(descriptor)).thenReturn(kaleObservableSubject)
    return messageSubject
}