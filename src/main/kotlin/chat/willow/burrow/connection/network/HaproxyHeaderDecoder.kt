package chat.willow.burrow.connection.network

import chat.willow.burrow.helper.loggerFor
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.ByteBuffer


interface IHaproxyFrameAccumulator {
    fun decode(input: HaproxyHeaderDecoder.Input): HaproxyHeaderDecoder.Output
}

class HaproxyHeaderDecoder : IHaproxyFrameAccumulator {

    private val LOGGER = loggerFor<HaproxyHeaderDecoder>()

    data class Input(val bytes: ByteArray, val bytesRead: Int)
    data class Output(val header: Header, val remainingBytes: ByteArray, val remainingBytesRead: Int)

    // todo: everything else - command (health checks), TLSV
    data class Header(val sourceAddress: String, val sourcePort: Int, val destinationAddress: String, val destinationPort: Int)

    object NoPrefixException : Exception()
    object PayloadMalformedException : Exception()
    object ProtocolNotVersion2 : Exception()
    object LocalCommandException : Exception()
    object UnsupportedCommandException : Exception()
    object UnspecifiedAddressFamily : Exception()
    object UnsupportedAddressFamily : Exception()
    object UnixSocketTodoException : Exception()
    object UnspecifiedTransportProtocol : Exception()
    object UnsupportedTransportProtocol : Exception()

    override fun decode(input: Input): Output {
        val bytes = input.bytes
        val bytesRead = input.bytesRead

        if (!startsWithPrefix(bytes, bytesRead)) {
            throw NoPrefixException
        }

        val buffer = ByteBuffer.wrap(bytes)
        buffer.position(12)

        val proxyProtocolAndCommand = buffer.get()
        val proxyProtocol = highestFourBits(proxyProtocolAndCommand)
        val command = lowestFourBits(proxyProtocolAndCommand)

        if (proxyProtocol != 2) {
            throw ProtocolNotVersion2
        }

        if (command == 0) {
            throw LocalCommandException
        } else if (command != 1) {
            throw UnsupportedCommandException
        }

        val addressFamilyAndTransportProtocol = buffer.get()
        val addressFamily = highestFourBits(addressFamilyAndTransportProtocol)
        val transportProtocol = highestFourBits(addressFamilyAndTransportProtocol)

        if (addressFamily == 0) {
            throw UnspecifiedAddressFamily
        } else if (addressFamily > 3) {
            throw UnsupportedAddressFamily
        }

        if (transportProtocol == 0) {
            throw UnspecifiedTransportProtocol
        } else if (transportProtocol > 2) {
            throw UnsupportedTransportProtocol
        }

        val payloadLength = readRemainingPayloadLength(buffer) ?: throw PayloadMalformedException
        val headerLength = payloadLength + 16

        var sourceAddress = ""
        var destinationAddress = ""
        var sourcePort = 0
        var destinationPort = 0

        // todo: support UNIX sockets
        when (addressFamily) {
            1 -> {
                // INET - src(4) dst(4) src_port(2) dst_port(2)
                sourceAddress = readInet4Address(buffer)
                destinationAddress = readInet4Address(buffer)
                sourcePort = readUnsignedShort(buffer)
                destinationPort = readUnsignedShort(buffer)
            }
            2 -> {
                // INET6 - src(1x16) dst(1x16) src_port(2) dst_port(2)
                sourceAddress = readInet6Address(buffer)
                destinationAddress = readInet6Address(buffer)
                sourcePort = readUnsignedShort(buffer)
                destinationPort = readUnsignedShort(buffer)
            }
            3 -> // UNIX - src(1x108) dst(1x108)
                throw UnixSocketTodoException
        }

        val header = Header(sourceAddress, sourcePort, destinationAddress, destinationPort)

        val bytesAfterHeaderLength = bytesRead - headerLength
        if (bytesAfterHeaderLength < 1) {
            return Output(remainingBytes = byteArrayOf(), remainingBytesRead = 0, header = header)
        }

        // todo: can we eliminate this copy with an index?
        val bytesAfterHeader = bytes.copyOfRange(headerLength, bytesRead)

        return Output(remainingBytes = bytesAfterHeader, remainingBytesRead = bytesAfterHeader.size, header = header)
    }

    private fun highestFourBits(byte: Byte): Int {
        return (byte.toInt() shr 4)
    }

    private fun lowestFourBits(byte: Byte): Int {
        return (byte.toInt() and 0x0F)
    }

    private fun startsWithPrefix(bytes: ByteArray, bytesRead: Int): Boolean {
        if (bytesRead < HAPROXY_V2_PREFIX.size) {
            return false
        }

        return (0 until HAPROXY_V2_PREFIX.size)
            .none { bytes[it] != HAPROXY_V2_PREFIX[it] }
    }

    private fun readRemainingPayloadLength(buffer: ByteBuffer): Int? {
        if (buffer.remaining() < 16) {
            return null
        }

        return readUnsignedShort(buffer)
    }

    private fun readUnsignedShort(buffer: ByteBuffer): Int {
        return buffer.short.toInt() and 0xffff
    }

    private fun readInet4Address(buffer: ByteBuffer): String {
        val byteBuffer = ByteBuffer.allocate(4)
        (0 until 4).forEach { byteBuffer.put(buffer.get()) }
        val sourceAddress = InetAddress.getByAddress(byteBuffer.array())
        return sourceAddress.hostAddress
    }

    private fun readInet6Address(buffer: ByteBuffer): String {
        val byteBuffer = ByteBuffer.allocate(16)
        (0 until 16).forEach { byteBuffer.put(buffer.get()) }

        val sourceAddress = Inet6Address.getByAddress(byteBuffer.array())
        return sourceAddress.hostAddress
    }

    companion object {
        val HAPROXY_V2_PREFIX = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A)
    }

}