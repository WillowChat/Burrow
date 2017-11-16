package chat.willow.burrow.connection.line

import chat.willow.burrow.connection.BurrowConnection
import chat.willow.burrow.helper.IInterruptedChecker
import chat.willow.burrow.helper.loggerFor
import chat.willow.burrow.kale.IBurrowKaleWrapper
import java.util.concurrent.LinkedBlockingQueue

typealias LineProcessingItem = Pair<BurrowConnection, String>

interface IIrcMessageProcessor: Runnable {

    operator fun plusAssign(item: LineProcessingItem)

}

class LineProcessor(private val interruptedChecker: IInterruptedChecker, private val burrowKaleWrapper: IBurrowKaleWrapper): IIrcMessageProcessor {

    private val LOGGER = loggerFor<LineProcessor>()

    private val queue = LinkedBlockingQueue<LineProcessingItem>()

    override fun run() {
        LOGGER.info("starting...")

        while (!interruptedChecker.isInterrupted) {
            val (client, line) = try {
                queue.take()
            } catch (exception: Exception) {
                LOGGER.warn("got exception, bailing out: $exception")

                return
            }

            burrowKaleWrapper.process(line, client.id)
        }

        LOGGER.info("thread interrupted, bailing out")
    }

    override fun plusAssign(item: LineProcessingItem) {
        queue.put(item)
    }

}