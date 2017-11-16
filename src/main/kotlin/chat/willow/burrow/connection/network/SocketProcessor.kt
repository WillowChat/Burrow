package chat.willow.burrow.connection.network

import chat.willow.burrow.connection.ConnectionId
import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import java.nio.ByteBuffer
import java.nio.channels.Selector

interface ISocketProcessorFactory {

    fun create(nioWrapper: INIOWrapper, buffer: ByteBuffer, delegate: ISocketProcessorDelegate, interruptedChecker: IInterruptedChecker): ISocketProcessor

}

object SocketProcessorFactory: ISocketProcessorFactory {

    override fun create(nioWrapper: INIOWrapper, buffer: ByteBuffer, delegate: ISocketProcessorDelegate, interruptedChecker: IInterruptedChecker): ISocketProcessor {
        return SocketProcessor(nioWrapper, buffer, delegate, interruptedChecker)
    }

}

interface ISocketProcessor: Runnable

interface ISocketProcessorDelegate {

    fun onAccepted(socket: INetworkSocket): ConnectionId
    fun onRead(id: ConnectionId, buffer: ByteBuffer, bytesRead: Int)
    fun onDisconnected(id: ConnectionId)

}


class SocketProcessor(private val nioWrapper: INIOWrapper, private val incomingBuffer: ByteBuffer, private val delegate: ISocketProcessorDelegate, private val interruptedChecker: IInterruptedChecker): ISocketProcessor {

    private val LOGGER = loggerFor<SocketProcessor>()

    override fun run() {
        LOGGER.info("starting...")

        while (!interruptedChecker.isInterrupted) {
            val keys = nioWrapper.select()

            for (key in keys) {
                when {
                    key.isAcceptable -> accept(key)
                    key.isReadable -> read(key)
                }
            }

            nioWrapper.clearSelectedKeys()
        }

        LOGGER.info("thread interrupted, bailing out")
    }

    private fun accept(key: ISelectionKeyWrapper) {
        val (socket, clientKey) = nioWrapper.accept(key.original)
        val id = delegate.onAccepted(socket)
        nioWrapper.attach(id, clientKey)
    }

    private fun read(key: ISelectionKeyWrapper) {
        val (bytesRead, id) = nioWrapper.read(key.original, incomingBuffer)

        if (bytesRead < 0) {
            nioWrapper.close(key.original)

            delegate.onDisconnected(id)

            return
        }

        delegate.onRead(id, buffer = incomingBuffer, bytesRead = bytesRead)
    }

}