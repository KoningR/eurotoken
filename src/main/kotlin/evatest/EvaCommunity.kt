package evatest

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay

class EvaCommunity : Community() {
    override val serviceId = "381entirelyrandomcommunitystringa141279f"
    private val logger = KotlinLogging.logger {}

    private val measureWindow = 2000
    private var startTime = -1L

    override fun getWalkableAddresses(): List<IPv4Address> {
        return emptyList()
    }

    internal fun startTimer() {
        if (startTime < 0) {
            startTime = System.currentTimeMillis()
            logger.info { "Start timing!" }
        }
    }

    internal fun stopTimer(totalBytes: Int) {
        val now = System.currentTimeMillis()

        logger.info { "Time since start: ${now - startTime}" }
        logger.info { "Bytes received: $totalBytes" }
    }

    class Factory() : Overlay.Factory<EvaCommunity>(EvaCommunity::class.java) {
        override fun create(): EvaCommunity {
            return EvaCommunity()
        }
    }
}