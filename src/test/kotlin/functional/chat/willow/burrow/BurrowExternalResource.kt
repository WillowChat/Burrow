package functional.chat.willow.burrow

import chat.willow.burrow.Burrow
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder
import chat.willow.burrow.connection.network.HaproxyHeaderDecoder.Companion.HAPROXY_V2_PREFIX
import chat.willow.burrow.helper.loggerFor
import org.junit.Assert
import org.junit.rules.ExternalResource
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class BurrowExternalResource: ExternalResource() {

    lateinit var burrow: Burrow
    lateinit var burrowThread: Thread
    lateinit var sockets: Set<Socket>

    private val LOGGER = loggerFor<BurrowExternalResource>()

    override fun before() {
        super.before()

        burrow = Burrow

        // todo: why doesn't java have a concurrenthashset?
        sockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

        burrowThread = thread(start = true) {
            burrow.main(arrayOf())
        }
    }

    override fun after() {
        sockets.forEach {
            if (!it.isClosed) {
                it.close()
            }
        }

        burrowThread.interrupt()
        burrowThread.join(3000)
        if (burrowThread.isAlive) {
            Assert.fail("Burrow did not shut down correctly (waited 3 seconds)")
        }

        super.after()
    }

    data class BurrowTestSocket(val socket: Socket, val output: PrintWriter, val input: BufferedReader, val rawOut: OutputStream) {
        private val LOGGER = loggerFor<BurrowExternalResource>()

        private fun ignoreNext(next: Int) {
            (0 until next).forEach {
                val line = input.readLine() ?: throw RuntimeException("Null line read")
                LOGGER.debug("Ignored line $line")
            }
        }

        fun ignorePreregistration() {
            ignoreNext(2)
        }
    }

    fun socket(host: String = "127.0.0.1", port: Int = 6770, ignorePreregistration: Boolean = true): BurrowTestSocket {
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

        socket.soTimeout = 1000

        val rawOut = socket.getOutputStream()
        val socketOut = PrintWriter(socket.getOutputStream(), true)
        val socketIn = BufferedReader(InputStreamReader(socket.getInputStream()))

        val returnSocket = BurrowTestSocket(socket, socketOut, socketIn, rawOut)

        if (ignorePreregistration) {
            returnSocket.ignorePreregistration()
        }

        this.sockets += socket
        return returnSocket
    }

    fun haproxySocket(content: ByteArray = byteArrayOf()): BurrowTestSocket {
        val socket = socket(port = 6771, ignorePreregistration = false)

        val inet4Length = 4 + 4 + 2 + 2
        val prefixLength = HAPROXY_V2_PREFIX.size + 1 + 1 + 2
        val bufferLength = prefixLength + inet4Length + content.size
        val buffer = ByteBuffer.allocate(bufferLength)
        buffer.put(HAPROXY_V2_PREFIX)
        buffer.put(0x21) // protocol version 2 + nonlocal command
        buffer.put(0x11) // inet4 stream
        buffer.putShort(inet4Length.toShort())
        (0 until inet4Length).forEach { buffer.put(0x00) }

        if (content.isNotEmpty()) {
            buffer.put(content)
        }

        socket.rawOut.write(buffer.array(), 0, bufferLength)
        socket.rawOut.flush()

        socket.ignorePreregistration()

        this.sockets += socket.socket
        return socket
    }

}