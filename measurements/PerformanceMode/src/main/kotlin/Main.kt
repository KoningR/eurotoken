import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.min
import kotlin.random.Random

const val LOCALHOST = "localhost"
// Max packet size of a UDP payload in bytes.
const val UDP_MAX_PACKET_SIZE = 1492

// Divide by 8 because a 'token' in this scenario is simply a Long,
// which is 8 bytes. Subtract 2 because the first 2 bytes in the
// packet will indicate how many tokens the packet contains.
const val MAX_TOKEN_SEND_AMOUNT = (UDP_MAX_PACKET_SIZE - 2) / 8

// To send 100 megabytes with packets of 1492 bytes = 67025 packets.
// With 186 tokens per packet, this means we need to generate
// 186 * 67025 = 12466650 tokens assuming no packets are lost.

const val TOKEN_AMOUNT_STOP_MEASURING = 12466650

// Create a buffer that will hold the payload of the packets we send.
val sendBuffer: ByteBuffer = ByteBuffer
    .allocate(UDP_MAX_PACKET_SIZE)
    .order(ByteOrder.nativeOrder())
// The payload of this packet points to the backing array of the buffer,
// so filling the buffer is the same as filling the payload.
// Getting the array up to the point of writing requires a copy,
// so we will send the full array and assume that most
// of the time it is almost full.
val sendPacket = DatagramPacket(sendBuffer.array(), UDP_MAX_PACKET_SIZE)

// Do the same for the receiving buffer and packet.
val receiveBuffer: ByteBuffer = ByteBuffer
    .allocate(UDP_MAX_PACKET_SIZE)
    .order(ByteOrder.nativeOrder())
val receivePacket = DatagramPacket(receiveBuffer.array(), UDP_MAX_PACKET_SIZE)

// The list of this client's 'tokens'.
lateinit var tokens: MutableSet<Long>

// Initialise the socket at the start of the program, because
// the program arguments define which ports to use.
lateinit var sendSocket: DatagramSocket

var startTime: Long = -1L
var endTime: Long = -1L

private fun send(amount: Int) {

    if (tokens.size < amount) {
        println("Insufficient balance!")
        return
    }

    var unsentAmount = amount

    while (unsentAmount > 0) {
        sendBuffer.position(0)

        // Send as many tokens as possible per packet.
        val sendAmount: Short = min(unsentAmount, MAX_TOKEN_SEND_AMOUNT).toShort()
        // The first 2 bytes of a packet indicate how many tokens will follow
        // in the packet.
        sendBuffer.putShort(sendAmount)

        val iter = tokens.iterator()
        repeat(sendAmount.toInt()) {
            sendBuffer.putLong(iter.next())
            iter.remove()
        }

        sendSocket.send(sendPacket)

        unsentAmount -= sendAmount
    }

    println("Sent $amount tokens! Balance is now ${tokens.size}")
}

private fun receive() {
    receiveBuffer.position(0)
    val receiveAmount = receiveBuffer.short

    repeat(receiveAmount.toInt()) {
        tokens.add(receiveBuffer.long)

        if (tokens.size == TOKEN_AMOUNT_STOP_MEASURING) {
            endTime = System.currentTimeMillis()

            // Calculation will not be exact; this code may be called
            // halfway through processing a packet.
            val approxBytesReceived = (TOKEN_AMOUNT_STOP_MEASURING / MAX_TOKEN_SEND_AMOUNT) * UDP_MAX_PACKET_SIZE
            val deltaTime = endTime - startTime
            val throughputMb = throughputMbPerSecond(approxBytesReceived, deltaTime)
            val throughputTx = throughputTxPerSecond(TOKEN_AMOUNT_STOP_MEASURING, deltaTime)

            println("Received $approxBytesReceived bytes in $deltaTime milliseconds.")
            println("Throughput was $throughputMb megabytes per second.")
            println("Throughput was $throughputTx transactions per second.")
        }
    }

    // In this scenario we assume everyone is acting fairly,
    // and thus we will never receive duplicates.
//        println("Received $receiveAmount tokens! Balance is now ${tokens.size}!")
}

private fun executeLine(inputString: String): Boolean {
    val input = inputString.lowercase(Locale.getDefault()).split(" ")

    when (input[0]) {
        "send" -> {
            if (input.size == 1) {
                send(tokens.size)
            } else {
                send(input[1].toInt())
            }
        }
        else -> {
            println("Unknown command")
        }
    }

    return true
}

/**
 * Convert bytes per millisecond to megabytes per second.
 */
private fun throughputMbPerSecond(bytes: Int, millis: Long): Double {
    return (bytes.toDouble() / 1000000) / (millis.toDouble() / 1000)
}

private fun throughputTxPerSecond(transactions: Int, millis: Long): Double {
    return transactions / (millis.toDouble() / 1000)

}

fun main(args: Array<String>) {

    val fromPort = args[0].toInt()
    val toPort = args[1].toInt()
    val generationAmount = args[2].toInt()

    sendSocket = DatagramSocket(fromPort)
    sendSocket.connect(InetAddress.getByName(LOCALHOST), toPort)

    tokens = (List(generationAmount) { Random.nextLong() }).toMutableSet()

    println("Balance is ${tokens.size}!")

    val thread = Thread {
        while (true) {
            sendSocket.receive(receivePacket)

            // Start timing from the arrival of the first packet.
            if (startTime == -1L) {
                startTime = System.currentTimeMillis()
            }

            receive()
        }
    }
    thread.start()

    while (true) {
        val input = readLine()!!

        if (!executeLine(input)) {
            break
        }
    }
}
