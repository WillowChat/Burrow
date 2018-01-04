package functional.chat.willow.burrow

import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer

class SanityFunctionalTests {

    @get:Rule val burrow = BurrowExternalResource()

    @Test fun `logging on as someone results in an MOTD targeted at them`() {
        val socket = burrow.socket()

        socket.output.println("NICK someone")
        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone :Welcome to Burrow Tests", response)
    }

    @Test fun `logging on as someone_ results in an MOTD targeted at them`() {
        val socket = burrow.socket()

        socket.output.println("NICK someone_")
        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :Welcome to Burrow Tests", response)
    }

    @Test fun `trying to register with an erroneous nick results in an error`() {
        val socket = burrow.socket()

        socket.output.println("NICK _someone")

        val response = socket.input.readLine()
        assertEquals(":🐰 432 _someone :Erroneous nickname", response)
    }

    @Test fun `registration via haproxy port, with content in the header frame`() {
        val socket = burrow.haproxySocket(content = "NICK someone_\r\n".toByteArray(Charsets.UTF_8))

        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :Welcome to Burrow Tests", response)
    }

    @Test fun `registration via haproxy port, without content in the header frame`() {
        val socket = burrow.haproxySocket()

        socket.output.println("NICK someone_")
        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :Welcome to Burrow Tests", response)
    }

}