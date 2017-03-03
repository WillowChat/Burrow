package chat.willow.burrow

import chat.willow.burrow.helper.loggerFor

object Burrow {

    private val LOGGER = loggerFor<Burrow>()

    @JvmStatic fun main(args: Array<String>) {
        LOGGER.info("hello, Burrow!")
    }

}