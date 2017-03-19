package chat.willow.burrow

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.irc.handler.*
import chat.willow.kale.IKale
import chat.willow.kale.IKaleHandler
import chat.willow.kale.irc.message.IrcMessage
import chat.willow.kale.irc.message.IrcMessageParser
import chat.willow.kale.irc.message.rfc1459.UserMessage
import chat.willow.kale.irc.tag.ITagStore
import java.util.concurrent.LinkedBlockingQueue

typealias LineProcessingItem = Pair<BurrowConnection, String>

interface IIrcMessageProcessor: Runnable {

    operator fun plusAssign(item: LineProcessingItem)

}

class LineProcessor(private val interruptedChecker: IInterruptedChecker): IIrcMessageProcessor {

    private val LOGGER = loggerFor<LineProcessor>()

    private val queue = LinkedBlockingQueue<LineProcessingItem>()

    private val handlers = mutableMapOf<String, IBurrowHandler>()

    init {
        handlers += (NickMessage.command to NickHandler())

        val capLsHandler = CapHandler.CapLsHandler()
        handlers += ("CAP" to capLsHandler)
    }

    override fun run() {
        LOGGER.info("starting...")

        while (!interruptedChecker.isInterrupted) {
            val (client, line) = try {
                queue.take()
            } catch (exception: Exception) {
                LOGGER.warn("got exception, bailing out: $exception")

                return
            }

            val message = IrcMessageParser.parse(line)
            if (message == null) {
                LOGGER.warn("failed to parse line from client $client: $line")
                continue
            }

            val handler = handlers[message.command]
            if (handler == null) {
                LOGGER.warn("no handler for message: $message")
                continue
            }

            handler.on(message)
        }

        LOGGER.info("thread interrupted, bailing out")
    }

    override fun plusAssign(item: LineProcessingItem) {
        queue.put(item)
    }

}