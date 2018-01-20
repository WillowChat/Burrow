package functional.chat.willow.burrow

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

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
        socket.input.readLine()

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :Welcome to Burrow Tests", response)
    }

    @Test fun `registration via haproxy port, without content in the header frame`() {
        val socket = burrow.haproxySocket()

        socket.output.println("NICK someone_")
        socket.output.println("USER 1 2 3 4")
        socket.input.readLine()

        val response = socket.input.readLine()
        assertEquals(":🐰 001 someone_ :Welcome to Burrow Tests", response)
    }


    @Test fun `register 100 plaintext clients in series`() {
        val numberOfClients = 500
        val list = (0 until numberOfClients).toList()

        // todo: try coroutines

        list.forEach {
            val socket = burrow.socket()

            socket.output.println("NICK someone$it")
            socket.output.println("USER 1 2 3 4")

            val response = socket.input.readLine()
            socket.socket.close()
            assertEquals(":🐰 001 someone$it :Welcome to Burrow Tests", response)
        }
    }

    @Test fun `register 500 plaintext clients in parallel`() {
        val numberOfClients = 500
        val list = (0 until numberOfClients).toList().parallelStream()

        // todo: try coroutines

        list.forEach {
            val socket = burrow.socket()

            socket.output.println("NICK someone$it")
            socket.output.println("USER 1 2 3 4")

            val response = socket.input.readLine()
            socket.socket.close()
            assertEquals(":🐰 001 someone$it :Welcome to Burrow Tests", response)
        }
    }


    @Test fun `register 100 haproxy clients in series`() {
        val numberOfClients = 100
        val list = (0 until numberOfClients).toList()

        // todo: try coroutines

        list.forEach {
            val socket = burrow.haproxySocket("NICK someone$it\r\nUSER 1 2 3 4\r\n".toByteArray())
            socket.input.readLine()

            val response = socket.input.readLine()
            socket.socket.close()
            assertEquals(":🐰 001 someone$it :Welcome to Burrow Tests", response)
        }
    }

    @Test fun `register 500 haproxy clients in parallel`() {
        val numberOfClients = 500
        val list = (0 until numberOfClients).toList().parallelStream()

        // todo: try coroutines

        list.forEach {
            val socket = burrow.haproxySocket("NICK someone$it\r\nUSER 1 2 3 4\r\n".toByteArray())
            socket.input.readLine()

            val response = socket.input.readLine()
            socket.socket.close()
            assertEquals(":🐰 001 someone$it :Welcome to Burrow Tests", response)
        }
    }

}