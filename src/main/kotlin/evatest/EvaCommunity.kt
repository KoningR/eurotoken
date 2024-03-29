package evatest

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import java.io.File

class EvaCommunity : Community() {
    override val serviceId = "381entirelyrandomcommunitystringa141279f"

    private val logger = KotlinLogging.logger {}

    private val transactionTimer = mutableMapOf<String, Long>().withDefault { -1L }

    override fun getWalkableAddresses(): List<IPv4Address> {
        return listOf(
            IPv4Address("127.0.0.1", 8090),
            IPv4Address("127.0.0.1", 8091)
        )
    }

    internal fun startTimer(id: String) {
        val startTime = System.currentTimeMillis()

        if (transactionTimer.getOrDefault(id, -1L) < 0) {
            // using milliseconds is acceptable for measurements of this order.
            transactionTimer[id] = startTime

            logger.info { "Start timing for transaction $id!" }
        }
    }

    internal fun stopTimer(numLostPackets:Int, totalBytes: Int, id: String) {
        val now = System.currentTimeMillis()

        val startTime = transactionTimer[id]!!

        File("EVA Window Block Gridsearch No Encryption.csv")
            .appendText("${throughputMbPerSecond(totalBytes, now - startTime)},")
        File("EVA Window Block Gridsearch Packets Lost No Encryption.csv")
            .appendText("$numLostPackets,")

        logger.info { "Megabytes per second: ${throughputMbPerSecond(totalBytes, now - startTime)}" }
        logger.info { "Time since start: ${now - startTime}" }
        logger.info { "Bytes received: $totalBytes" }
        logger.info { "Current time: $now" }
        logger.info { "Number of lost packets: $numLostPackets" }
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