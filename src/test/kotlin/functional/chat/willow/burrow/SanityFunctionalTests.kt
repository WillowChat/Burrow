package functional.chat.willow.burrow

import chat.willow.burrow.Burrow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import kotlin.concurrent.thread

class SanityFunctionalTests {

    lateinit var burrow: Burrow
    lateinit var burrowThread: Thread

    @Before fun setUp() {
        burrow = Burrow

        burrowThread = thread(start = true) {
            burrow.main(arrayOf())
        }

        // todo: replace with non-dumb sleep with a timeout
        Thread.sleep(1000)
    }

    @Test fun `logging on as "someone" results in an MOTD targeted at them`() {
        val socket = Socket("localhost", 6667)
        socket.keepAlive = false
        socket.soTimeout = 1000

        val socketOut = PrintWriter(socket.getOutputStream(), true)
        val socketIn = BufferedReader(InputStreamReader(socket.getInputStream()))

        socketOut.println("NICK someone\r\nUSER 1 2 3 4")

        val response = socketIn.readLine()
        assertEquals(":bunnies. 001 someone :welcome to burrow", response)
    }

    @Test fun `logging on as "someone_" results in an MOTD targeted at them`() {
        val socket = Socket("localhost", 6667)
        socket.keepAlive = false
        socket.soTimeout = 1000

        val socketOut = PrintWriter(socket.getOutputStream(), true)
        val socketIn = BufferedReader(InputStreamReader(socket.getInputStream()))

        socketOut.println("NICK someone_\r\nUSER 1 2 3 4")

        val response = socketIn.readLine()
        assertEquals(":bunnies. 001 someone_ :welcome to burrow", response)
    }

    @After fun tearDown() {
        burrowThread.interrupt()
        burrowThread.join()
    }

}