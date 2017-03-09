package chat.willow.burrow

import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.kale.irc.message.IrcMessage
import java.util.concurrent.LinkedBlockingQueue

typealias MessageProcessingItem = Pair<BurrowClient, IrcMessage>

interface IIrcMessageProcessor: Runnable {

    operator fun plusAssign(item: MessageProcessingItem)

}

class IrcMessageProcessor(private val interruptedChecker: IInterruptedChecker): IIrcMessageProcessor {

    private val LOGGER = loggerFor<IrcMessageProcessor>()

    private val queue = LinkedBlockingQueue<MessageProcessingItem>()

    override fun run() {
        LOGGER.info("starting...")

        while (!interruptedChecker.isInterrupted) {
            val (client, ircMessage) = try {
                queue.take()
            } catch (exception: Exception) {
                LOGGER.warn("got exception, bailing out: $exception")

                return
            }

            LOGGER.info("client $client sent irc message: $ircMessage")
        }

        LOGGER.info("thread interrupted, bailing out")
    }

    override fun plusAssign(item: MessageProcessingItem) {
        queue.put(item)
    }

}