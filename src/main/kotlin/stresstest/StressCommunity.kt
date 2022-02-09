package stresstest

import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.messaging.Packet
import java.io.File

class StressCommunity : Community() {
    override val serviceId = "381a9685c1912a141279f8222193db58u9c5duck"
    private val logger = KotlinLogging.logger {}

    private val measureWindow = 500

    private var startTime = -1L
    private var received = 0
    private val times = Array(measureWindow) { Pair(0, 0L) }

    init {
        messageHandlers[MessageId.STRESS_MESSAGE] = ::onStressMessage
    }

    private fun onStressMessage(packet: Packet) {
        if (startTime < 0) {
            startTime = System.currentTimeMillis()
            logger.info { "Received a first packet of size ${packet.data.size}!" }
        }

        val now = System.currentTimeMillis()
        times[received] = Pair(received + 1, now - startTime)

        received += 1
        if (received == measureWindow) {
            logger.info { "Received $measureWindow transactions!" }
            logger.info { "Time since start: ${now - startTime}" }
            logger.info { "Bytes received: ${measureWindow * 1500}" }

            File("timing1.csv").bufferedWriter().use { out ->
                out.write("transactions,times\n")
                times.forEach { pair ->
                    out.write("${pair.first},${pair.second}\n")
                }
            }
        }
    }

    object MessageId {
        const val STRESS_MESSAGE = 1
    }

    class Factory() : Overlay.Factory<StressCommunity>(StressCommunity::class.java) {
        override fun create(): StressCommunity {
            return StressCommunity()
        }
    }
}