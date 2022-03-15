import java.net.DatagramPacket
import java.net.DatagramSocket

/**
 * Convert bytes per millisecond to megabytes per second.
 */
fun throughputMbPerSecond(bytes: Int, millis: Long): Double {
    return (bytes.toDouble() / 1000000) / (millis.toDouble() / 1000)
}

fun main() {
    // The sender process will sometimes send packets of the same
    // size and thus send a bit more than SEND_DATA_SIZE bytes.
    val data = ByteArray(MEASURE_DATA_SIZE + PAYLOAD_SIZE)

    // Bind the receiver process's destination port.
    val socket = DatagramSocket(TO_PORT)

    // Wait for the first packet to arrive. This can also
    // be done in the loop but this way we don't need
    // an additional condition for measuring the start time
    // once.
    var readBytes = 0
    val firstPacket = DatagramPacket(data, readBytes, PAYLOAD_SIZE)
    socket.receive(firstPacket)

    val startTime = System.currentTimeMillis()

    // Keep waiting for packets until enough bytes are read.
    readBytes = firstPacket.length
    while (readBytes < MEASURE_DATA_SIZE) {
        val packet = DatagramPacket(data, readBytes, PAYLOAD_SIZE)
        socket.receive(packet)

        readBytes += packet.length
    }

    val duration = System.currentTimeMillis() - startTime

    println("Read $readBytes bytes in $duration milliseconds.")
    println("Throughput was ${throughputMbPerSecond(readBytes, duration)} megabytes per second.")
}