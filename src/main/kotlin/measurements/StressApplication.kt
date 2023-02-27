package measurements

import mu.KotlinLogging
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import java.net.InetAddress

class StressApplication {
    private val logger = KotlinLogging.logger {}

    private val udpEndpoint: UdpEndpoint = UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))
    private val stressCommunity: StressCommunity

    init {
        val myPrivateKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(myPrivateKey)

        val endpointAggregator = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createStressCommunity()
        ), walkerInterval = 1.0)

        val ipv8 = IPv8(endpointAggregator, config, myPeer)
        ipv8.start()

        stressCommunity = ipv8.getOverlay()!!
    }

    fun printInfo(): Int {
        val peers = stressCommunity.getPeers()
        return peers.size
    }

    fun test() {
        if (stressCommunity.getPeers().isEmpty()) {
            return
        }

        val recipient = stressCommunity.getPeers().first()

        // Because we don't use any acknowledgement protocol for this
        // measurement, we expect a huge packet loss and therefore send
        // many more packets than we need to measure.
        repeat(StressCommunity.NUM_PACKETS_MEASURED * 3) {

            val payload = StressPayload()
            val packet = stressCommunity.serializePacket(
                StressCommunity.MessageId.STRESS_MESSAGE,
                payload,
                sign = false,
                encrypt = true,
                recipient = recipient
            )

            stressCommunity.endpoint.send(recipient, packet)
        }

        logger.info { "Sent packet(s)" }
    }

    fun reset() {
        stressCommunity.reset()
    }

    private fun createStressCommunity(): OverlayConfiguration<StressCommunity> {
        return OverlayConfiguration(
            StressCommunity.Factory(),
            listOf(RandomWalk.Factory(timeout = 3.0, peers = 1))
        )
    }
}