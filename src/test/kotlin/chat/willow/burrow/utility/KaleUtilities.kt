package chat.willow.burrow.utility

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.kale.IKale
import chat.willow.kale.core.message.KaleDescriptor
import chat.willow.kale.helper.CaseInsensitiveNamedMap
import chat.willow.kale.helper.INamed
import chat.willow.kale.irc.prefix.Prefix
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject
import unit.chat.willow.burrow.state.message


data class TestClient(val kale: IKale, val client: ClientTracker.ConnectedClient) {
    fun <T> mock(descriptor: KaleDescriptor<T>): PublishSubject<T> {
        return mockKaleObservable(kale, descriptor)
    }

    val prefix: Prefix
        get() = client.prefix

    val id: ConnectionId
        get() = client.connectionId

}

fun makeClient(kale: IKale = KaleUtilities.mockKale(), id: ConnectionId = 1, prefix: Prefix = chat.willow.kale.irc.prefix.prefix("someone")): TestClient {
    val connection = BurrowConnection(id = id, primitiveConnection = mock())

    return TestClient(kale = kale, client = ClientTracker.ConnectedClient(connection, kale, prefix))
}

fun <T : INamed> namedMap(contents: List<T> = listOf()): CaseInsensitiveNamedMap<T> {
    val store = CaseInsensitiveNamedMap<T>(mapper = Burrow.Server.MAPPER)
    contents.forEach { store.put(it) }
    return store
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