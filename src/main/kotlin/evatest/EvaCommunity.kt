package evatest

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay

class EvaCommunity : Community() {
    override val serviceId = "381entirelyrandomcommunitystringa141279f"
    private val logger = KotlinLogging.logger {}

    private var startTime = -1L

    override fun getWalkableAddresses(): List<IPv4Address> {
        return listOf(
            IPv4Address("127.0.0.1", 8090),
            IPv4Address("127.0.0.1", 8091)
        )
    }

    internal fun startTimer() {
        if (startTime < 0) {
            startTime = System.currentTimeMillis()
            logger.info { "Start timing!" }
        }
    }

    internal fun stopTimer(totalBytes: Int) {
        val now = System.currentTimeMillis()

        logger.info { "Megabytes per second: ${throughputMbPerSecond(totalBytes, now - startTime)}" }
        logger.info { "Time since start: ${now - startTime}" }
        logger.info { "Bytes received: $totalBytes" }
    }

    private fun throughputMbPerSecond(bytes: Int, millis: Long): Double {
        return (bytes.toDouble() / 1000000) / (millis.toDouble() / 1000)
    }

    class Factory() : Overlay.Factory<EvaCommunity>(EvaCommunity::class.java) {
        override fun create(): EvaCommunity {
            return EvaCommunity()
        }
    }
}