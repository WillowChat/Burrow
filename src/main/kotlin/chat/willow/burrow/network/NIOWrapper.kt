package chat.willow.burrow.network

import chat.willow.burrow.ClientId
import chat.willow.burrow.network.INetworkSocket
import chat.willow.burrow.network.ISelectorFactory
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel

class NIOSocketChannelWrapper(private val socket: SocketChannel): INetworkSocket {

    override val isConnected: Boolean
        get() = socket.isConnected

    override fun close() {
        socket.close()
    }

}

interface ISelectionKeyWrapper {

    val isAcceptable: Boolean
    val isReadable: Boolean

    val original: SelectionKey

}

class SelectionKeyWrapper(override val original: SelectionKey): ISelectionKeyWrapper {
    override val isAcceptable: Boolean
        get() = original.isAcceptable

    override val isReadable: Boolean
        get() = original.isReadable

}

interface INIOWrapper {

    fun setUp(address: InetSocketAddress)
    fun select(): MutableSet<ISelectionKeyWrapper>
    fun clearSelectedKeys()
    fun accept(key: SelectionKey): Pair<INetworkSocket, SelectionKey>
    fun attach(id: ClientId, key: SelectionKey)
    fun read(key: SelectionKey, buffer: ByteBuffer): Pair<Int, ClientId>
    fun close(key: SelectionKey)

}

class NIOWrapper(val selectorFactory: ISelectorFactory): INIOWrapper {

    private lateinit var selector: Selector

    override fun setUp(address: InetSocketAddress) {
        val channel = ServerSocketChannel.open()
        channel.bind(address)
        channel.configureBlocking(false)

        selector = selectorFactory.create()
        val ops = channel.validOps()
        channel.register(selector, ops)
    }

    override fun select(): MutableSet<ISelectionKeyWrapper> {
        var selected = 0
        while (selected <= 0) {
            selected = selector.select()
        }

        return selector.selectedKeys().map(::SelectionKeyWrapper).toMutableSet()
    }

    override fun clearSelectedKeys() {
        selector.selectedKeys().clear()
    }

    override fun accept(key: SelectionKey): Pair<INetworkSocket, SelectionKey> {
        val channel = key.channel() as ServerSocketChannel
        val socket = channel.accept()
        socket.configureBlocking(false)

        val clientKey = socket.register(selector, SelectionKey.OP_READ)

        return NIOSocketChannelWrapper(socket) to clientKey
    }

    override fun attach(id: ClientId, key: SelectionKey) {
        key.attach(id)
    }

    override fun read(key: SelectionKey, buffer: ByteBuffer): Pair<Int, ClientId> {
        val channel = key.channel() as SocketChannel
        val id = key.attachment() as ClientId

        buffer.clear()

        return channel.read(buffer) to id
    }

    override fun close(key: SelectionKey) {
        val channel = key.channel() as SocketChannel

        channel.close()
        key.cancel()
    }

}
