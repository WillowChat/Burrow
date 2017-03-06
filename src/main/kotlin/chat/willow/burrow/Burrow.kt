package chat.willow.burrow

import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.IrcMessageParser
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SelectionKey.OP_READ
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

val NEW_LINE_BYTE = '\n'.toByte()
val CARRIAGE_RETURN_BYTE = '\r'.toByte()

data class BurrowConnection(val id: Int, val socket: SocketChannel, val buffer: ByteBuffer, val accumulator: ILineAccumulator)

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("starting server...")

        val server = Server()
        server.start()

        LOGGER.info("server ended")
    }

    class Server : ILineAccumulatorDelegate {

        companion object {
            val BUFFER_SIZE = 4096
            val MAX_LINE_LENGTH = BUFFER_SIZE
            val UTF_8: Charset = Charset.forName("UTF-8")
            val NEXT_CLIENT_ID = AtomicInteger(0)
        }

        val clients = mutableMapOf<Int, BurrowConnection>()

        fun start() {
            val selector = Selector.open()

            val socket = ServerSocketChannel.open()
            val address = InetSocketAddress("0.0.0.0", 6667)
            socket.bind(address)
            socket.configureBlocking(false)

            val ops = socket.validOps()
            val selectorKey = socket.register(selector, ops)

            while (true) {
                selector.select()

                val keys = selector.selectedKeys()
                val iterator = keys.iterator()

                while (iterator.hasNext()) {
                    val key = iterator.next()

                    if (key.isAcceptable) {
                        accept(selector, key)
                    } else if (key.isReadable) {
                        read(key)
                    }
                }

                iterator.remove()
            }
        }

        private fun accept(selector: Selector, key: SelectionKey) {
            val serverChannel = key.channel() as? ServerSocketChannel ?: return
            val client = serverChannel.accept()

            client.configureBlocking(false)

            val nextClientId = NEXT_CLIENT_ID.incrementAndGet()

            val accumulator = LineAccumulator(bufferSize = MAX_LINE_LENGTH, delegate = this, connectionId = nextClientId)
            val info = BurrowConnection(nextClientId, socket = client, buffer = ByteBuffer.allocate(BUFFER_SIZE), accumulator = accumulator)
            val newClientKey = client.register(selector, OP_READ)
            newClientKey.attach(nextClientId)

            clients[nextClientId] = info

            LOGGER.info("accepted connection ${info.id}: $client")
        }

        private fun read(key: SelectionKey) {
            val client = key.channel() as? SocketChannel ?: return
            val attachment = key.attachment()
            val id = attachment as? Int ?: return

            val info = clients[id] ?: return

            val buffer = info.buffer
            buffer.clear()

            val bytesRead = client.read(buffer)
            if (bytesRead < 0) {
                LOGGER.info("client ${info.id} disconnected: $client")
                client.close()
                key.cancel()
                clients -= info.id

                return
            }

            LOGGER.info("client ${info.id} sent $bytesRead bytes, accumulating...")

            info.accumulator.add(buffer.array(), bytesRead)
        }

        override fun onBufferOverran(connectionId: Int) {
            val client = clients[connectionId] ?: return
            LOGGER.info("client ${client.id} onBufferOverran, disconnecting them")
            client.socket.close()
        }

        override fun onLineAccumulated(connectionId: Int, line: String) {
            val client = clients[connectionId] ?: return
            LOGGER.info("client ${client.id} sent line: $line")

            val ircMessage = IrcMessageParser.parse(line)
            if (ircMessage == null) {
                LOGGER.warn("client ${client.id} sent malformed message")
                client.socket.close()
            } else {
                LOGGER.info("client ${client.id} sent irc message: $ircMessage")
            }
        }
    }

}