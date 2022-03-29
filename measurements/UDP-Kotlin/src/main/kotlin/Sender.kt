import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

const val RECEIVER_ADDRESS = "192.168.178.220"
const val FROM_PORT = 25595
const val TO_PORT = 25535
const val PAYLOAD_SIZE = 1472

const val MEASURE_DATA_SIZE = 100000000
// Because UDP loses messages, the sender process
// sends more data than the receiver process will receive.
const val TOTAL_DATA_SIZE = 2 * MEASURE_DATA_SIZE

/**
 * Resend the same UDP packet each time.
 */
fun useSamePacket() {
    // Fill the array that will be sent.
    val data = ByteArray(TOTAL_DATA_SIZE)
    data.fill(0x42)

    // Bind the port and create the fixed packet.
    val receiver = InetAddress.getByName(RECEIVER_ADDRESS)
    val packet = DatagramPacket(data, PAYLOAD_SIZE, receiver, TO_PORT)
    val socket = DatagramSocket(FROM_PORT)

    // Keep sending packets until enough bytes are sent.
    // Note that there is no guarantee that MEASURE_DATA_SIZE
    // bytes will actually arrive; we simply assume that
    // sending TOTAL_DATA_SIZE bytes is sufficient.
    var sentBytes = 0
    while (sentBytes < TOTAL_DATA_SIZE) {
        socket.send(packet)

        sentBytes += PAYLOAD_SIZE
    }
}

/**
 * Create a new UDP packet each time.
 */
fun createNewPacket() {
    // Fill the array that will be sent.
    val data = ByteArray(TOTAL_DATA_SIZE)
    data.fill(0x42)

    // Bind the port.
    val receiver = InetAddress.getByName(RECEIVER_ADDRESS)
    val socket = DatagramSocket(FROM_PORT)

    // Keep sending packets until enough bytes are sent.
    // Note that there is no guarantee that MEASURE_DATA_SIZE
    // bytes will actually arrive; we simply assume that
    // sending TOTAL_DATA_SIZE bytes is sufficient.
    var sentBytes = 0
    while (sentBytes < TOTAL_DATA_SIZE) {
        // Recreate the packet with each send.
        // The packet's length is either PAYLOAD_SIZE
        // or smaller when we send the very last packet.
        val packet = DatagramPacket(
            data, sentBytes,
            minOf(PAYLOAD_SIZE, TOTAL_DATA_SIZE - sentBytes),
            receiver, TO_PORT)
        socket.send(packet)

        sentBytes += PAYLOAD_SIZE
    }
}

fun main() {
//    useSamePacket()
    createNewPacket()
}