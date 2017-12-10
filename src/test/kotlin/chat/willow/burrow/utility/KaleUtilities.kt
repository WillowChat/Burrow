package chat.willow.burrow.utility

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.connection.line.LineAccumulator
import chat.willow.burrow.connection.network.ConnectionId
import chat.willow.burrow.state.ClientTracker
import chat.willow.kale.IKale
import chat.willow.kale.KaleDescriptor
import chat.willow.kale.irc.prefix.Prefix
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.subjects.PublishSubject

fun makeClient(kale: IKale, id: ConnectionId = 1, prefix: Prefix = chat.willow.kale.irc.prefix.prefix("someone")): ClientTracker.ConnectedClient {
    val accumulator = LineAccumulator(bufferSize = 1)
    val connection = BurrowConnection(id = id, host = prefix.host ?: "", socket = mock(), accumulator = accumulator)

    return ClientTracker.ConnectedClient(connection, kale, prefix)
}

object KaleUtilities {

    fun mockKale(): IKale {
        val mockKale = mock<IKale>()
        whenever(mockKale.observe(any<KaleDescriptor<*>>())).thenReturn(PublishSubject.create())
        return mockKale
    }

}