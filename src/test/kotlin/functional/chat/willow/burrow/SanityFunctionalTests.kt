package functional.chat.willow.burrow

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SanityFunctionalTests {

    @get:Rule val burrow = BurrowExternalResource()

    @Test fun `logging on as "someone" results in an MOTD targeted at them`() {
        val socket = burrow.socket()

        socket.output.println("NICK someone")
        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone :welcome to burrow", response)
    }

    @Test fun `logging on as "someone_" results in an MOTD targeted at them`() {
        val socket = burrow.socket()

        socket.output.println("NICK someone_")
        socket.output.println("USER 1 2 3 4")

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :welcome to burrow", response)
    }

}