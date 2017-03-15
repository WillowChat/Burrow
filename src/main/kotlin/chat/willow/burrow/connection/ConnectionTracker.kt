package chat.willow.burrow.connection

import chat.willow.burrow.ILineAccumulatorListener
import chat.willow.burrow.ILineAccumulatorPool
import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.network.INetworkSocket
import chat.willow.kale.*
import chat.willow.kale.irc.message.extension.cap.CapLsMessage
import chat.willow.kale.irc.message.rfc1459.NickMessage
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.tag.IKaleTagRouter
import chat.willow.kale.irc.tag.ITagStore
import chat.willow.kale.irc.tag.KaleTagRouter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

typealias ConnectionId = Int

interface IConnectionTracker {

    fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowConnection
    fun drop(id: ConnectionId)

    operator fun get(id: ConnectionId): BurrowConnection?
    operator fun minusAssign(id: ConnectionId)

}

interface IKaleFactory {

    fun create(messageRouter: IKaleRouter = KaleRouter().useDefaults(), tagRouter: IKaleTagRouter = KaleTagRouter().useDefaults()): IKale

}

object KaleFactory: IKaleFactory {

    override fun create(messageRouter: IKaleRouter, tagRouter: IKaleTagRouter): IKale {
        return Kale(messageRouter, tagRouter)
    }
}

class ConnectionTracker(private val lineAccumulatorPool: ILineAccumulatorPool, private val kaleFactory: IKaleFactory): IConnectionTracker {

    private var nextConnectionId = AtomicInteger(0)
    private val connections: MutableMap<ConnectionId, BurrowConnection> = ConcurrentHashMap()

    override fun track(socket: INetworkSocket, listener: ILineAccumulatorListener): BurrowConnection {
        val id = nextConnectionId.getAndIncrement()
        val accumulator = lineAccumulatorPool.next(id, listener)
        val kale = kaleFactory.create()

        kale.register(object : IKaleHandler<UserMessage> {
            override val messageType = UserMessage::class.java
            private val LOGGER = loggerFor<UserMessage>()

            override fun handle(message: UserMessage, tags: ITagStore) {
                LOGGER.info("connection $id sent USER message: $message $tags")
            }
        })

        kale.register(object : IKaleHandler<NickMessage> {
            override val messageType = NickMessage::class.java
            private val LOGGER = loggerFor<NickMessage>()

            override fun handle(message: NickMessage, tags: ITagStore) {
                LOGGER.info("connection $id sent NICK message: $message $tags")
            }
        })

        kale.register(object : IKaleHandler<CapLsMessage> {
            override val messageType = CapLsMessage::class.java
            private val LOGGER = loggerFor<CapLsMessage>()

            override fun handle(message: CapLsMessage, tags: ITagStore) {
                LOGGER.info("connection $id sent CAP LS message: $message $tags")
            }
        })

        val connection =  BurrowConnection(id, socket = socket, accumulator = accumulator, kale = kale)

        connections[id] = connection

        return connection
    }

    override fun drop(id: ConnectionId) {
        connections.remove(id)
    }

    override fun get(id: ConnectionId): BurrowConnection? {
        return connections[id]
    }

    override fun minusAssign(id: ConnectionId) {
        drop(id)
    }

}