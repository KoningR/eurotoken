package measurements

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.messaging.Packet
import kotlin.math.ceil

class StressCommunity : Community() {

    override val serviceId = "381a9685c1912a141279f8222193db58u9c5duck"
    private val logger = KotlinLogging.logger {}

    private var startTime = -1L
    private var received = 0

    init {
        messageHandlers[MessageId.STRESS_MESSAGE] = ::onStressMessage
    }

    override fun getWalkableAddresses(): List<IPv4Address> {
        return listOf(
            IPv4Address("127.0.0.1", 8090),
            IPv4Address("127.0.0.1", 8091)
        )
    }

    private fun onStressMessage(packet: Packet) {
        val now = System.nanoTime()

        if (startTime < 0) {
            startTime = now
        }

        received += 1

        if (received == NUM_PACKETS_MEASURED) {
            val duration = now - startTime

            // This will be almost exactly 100 megabytes in bytes.
            val receivedBytes = NUM_PACKETS_MEASURED * StressPayload.TEST_PAYLOAD.size

            logger.info { "Throughput in megabytes per second: ${throughputMbPerSecond(receivedBytes, duration)}" }
            logger.info { "Duration in nanoseconds: $duration" }
            logger.info { "Bytes received: $receivedBytes" }
        }
    }

    private fun throughputMbPerSecond(bytes: Int, nanos: Long): Double {
        return (bytes.toDouble() / 1000000) / (nanos.toDouble() / 1000000000)
    }

    companion object {
        // 100 megabytes split over the size of the payload.
        internal val NUM_PACKETS_MEASURED = ceil((1e+8 / StressPayload.TEST_PAYLOAD.size)).toInt()
    }

    object MessageId {
        const val STRESS_MESSAGE = 1
    }

    class Factory : Overlay.Factory<StressCommunity>(StressCommunity::class.java) {
        override fun create(): StressCommunity {
            return StressCommunity()
        }
    }
}