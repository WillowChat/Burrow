package functional.chat.willow.burrow

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import org.junit.rules.ExternalResource
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
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

    data class BurrowTestSocket(val socket: Socket, val output: PrintWriter, val input: BufferedReader, val rawOut: OutputStream)

    fun socket(host: String = "127.0.0.1", port: Int = 6770): BurrowTestSocket {
        var socket: Socket? = null
        retry@for (i in 0..30) {
            try {
                socket = Socket(host, port)
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

        val rawOut = socket.getOutputStream()
        val socketOut = PrintWriter(socket.getOutputStream(), true)
        val socketIn = BufferedReader(InputStreamReader(socket.getInputStream()))

        return BurrowTestSocket(socket, socketOut, socketIn, rawOut)
    }

    fun haproxySocket(content: ByteArray = byteArrayOf()): BurrowTestSocket {
        val socket = socket(port = 6771)

        val inet4Length = 4 + 4 + 2 + 2
        val buffer = ByteBuffer.allocate(16 + inet4Length + content.size)
        buffer.put(HaproxyHeaderDecoder.HAPROXY_V2_PREFIX)
        buffer.put(0x21) // protocol version 2 + nonlocal command
        buffer.put(0x11) // inet4 stream
        buffer.putShort(inet4Length.toShort())
        (0 until inet4Length).forEach { buffer.put(0x00) }

        if (content.isNotEmpty()) {
            buffer.put(content)
        }

        socket.rawOut.write(buffer.array())

        return socket
    }

}