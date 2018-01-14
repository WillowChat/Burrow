package chat.willow.burrow.connection.network

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.connection.IPrimitiveConnection
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.*

class NIOPrimitiveConnection(private val internalSocket: SocketChannel, override var address: InetAddress):
    IPrimitiveConnection {

    override var host: String = address.hostAddress

    override fun close() {
        internalSocket.close()
    }

    override fun write(bytes: ByteBuffer) {
        internalSocket.write(bytes)
    }

    override fun toString(): String {
        return "{$host}"
    }
}

interface ISelectionKeyWrapper {

    val isAcceptable: Boolean
    val isReadable: Boolean

    val original: SelectionKey

}

class SelectionKeyWrapper(override val original: SelectionKey): ISelectionKeyWrapper {

    override val isAcceptable: Boolean
        get() = try { original.isAcceptable } catch (exception: CancelledKeyException) { false }

    override val isReadable: Boolean
        get() = try { original.isReadable } catch (exception: CancelledKeyException) { false }

}

object SelectorFactory : ISelectorFactory {
    override fun create(): Selector {
        return Selector.open()
    }
}

interface ISelectorFactory {
    fun create(): Selector
}

interface INIOWrapper {

    fun setUp(address: InetSocketAddress)
    fun tearDown()
    fun select(): MutableSet<ISelectionKeyWrapper>
    fun clearSelectedKeys()
    fun accept(key: SelectionKey): Pair<IPrimitiveConnection, SelectionKey>
    fun attach(id: ConnectionId, key: SelectionKey)
    fun read(key: SelectionKey, buffer: ByteBuffer): Pair<Int, ConnectionId>
    fun close(key: SelectionKey)

}

class NIOWrapper(private val selectorFactory: ISelectorFactory): INIOWrapper {

    private lateinit var selector: Selector
    private var channel: ServerSocketChannel? = null

    override fun setUp(address: InetSocketAddress) {
        val channel = ServerSocketChannel.open()

        channel.bind(address)
        channel.configureBlocking(false)

        selector = selectorFactory.create()
        val ops = channel.validOps()
        channel.register(selector, ops)

        this.channel = channel
    }

    override fun tearDown() {
        channel?.close()
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

    override fun accept(key: SelectionKey): Pair<IPrimitiveConnection, SelectionKey> {
        val channel = key.channel() as ServerSocketChannel
        val socket = channel.accept()
        socket.configureBlocking(false)

        val clientKey = socket.register(selector, SelectionKey.OP_READ)
        val address = (socket.remoteAddress as InetSocketAddress)
        return NIOPrimitiveConnection(socket, address.address) to clientKey
    }

    override fun attach(id: ConnectionId, key: SelectionKey) {
        key.attach(id)
    }

    override fun read(key: SelectionKey, buffer: ByteBuffer): Pair<Int, ConnectionId> {
        val channel = key.channel() as SocketChannel
        val id = key.attachment() as ConnectionId

        buffer.clear()

        return try {
            channel.read(buffer) to id
        } catch (exception: IOException) {
            -1 to id
        }
    }

    override fun close(key: SelectionKey) {
        val channel = key.channel() as SocketChannel

        channel.close()
        key.cancel()
    }

}
