package chat.willow.burrow.helper

interface IInterruptedChecker {

    val isInterrupted: Boolean

}

object ThreadInterruptedChecker: IInterruptedChecker {

    override val isInterrupted: Boolean
        get() = Thread.currentThread().isInterrupted

}