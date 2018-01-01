package functional.chat.willow.burrow

import chat.willow.burrow.Burrow
import org.junit.rules.ExternalResource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class BurrowExternalResource: ExternalResource() {

    lateinit var burrow: Burrow
    lateinit var burrowThread: Thread

    override fun before() {
        super.before()

        burrow = Burrow

        burrowThread = thread(start = true) {
            burrow.main(arrayOf())
        }
    }

    override fun after() {
        burrowThread.interrupt()
        burrowThread.join(3000)
        if (burrowThread.isAlive) {
            throw IllegalStateException("Burrow did not shut down correctly (waited 3 seconds)")
        }

        super.after()
    }

    data class BurrowTestSocket(val socket: Socket, val output: PrintWriter, val input: BufferedReader)

    fun socket(): BurrowTestSocket {
        var socket: Socket? = null
        retry@for (i in 0..30) {
            try {
                socket = Socket("localhost", 6789)
                break@retry
            } catch (exception: IOException) {
                Thread.sleep(100)
            }
        }

        if (socket == null) {
            throw IllegalStateException("Couldn't connect to Burrow")
        }

        socket.keepAlive = false
        socket.soTimeout = 1000

        val socketOut = PrintWriter(socket.getOutputStream(), true)
        val socketIn = BufferedReader(InputStreamReader(socket.getInputStream()))

        return BurrowTestSocket(socket, socketOut, socketIn)
    }

}