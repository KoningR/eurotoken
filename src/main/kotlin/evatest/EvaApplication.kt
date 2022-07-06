package evatest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.eva.EVAProtocol
import nl.tudelft.ipv8.messaging.eva.TransferProgress
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import java.net.InetAddress
import kotlin.math.floor

class EvaApplication {
    private val logger = KotlinLogging.logger {}
    private val dispatcher = Dispatchers.IO
    private val scope = CoroutineScope(dispatcher)

    private val udpEndpoint: UdpEndpoint = UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))
    private val evaCommunity: EvaCommunity

    init {
        val myPrivateKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(myPrivateKey)
//        val myPublicKey = myPeer.publicKey.keyToBin()

        val endpointAggregator = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createEvaCommunity()
        ), walkerInterval = 1.0)

        val ipv8 = IPv8(endpointAggregator, config, myPeer)
        ipv8.start()

        evaCommunity = ipv8.getOverlay()!!
        evaCommunity.evaProtocol = EVAProtocol(evaCommunity, scope, retransmitInterval = 150L)
        evaCommunity.setOnEVAReceiveProgressCallback(::onEvaProgress)
        evaCommunity.setOnEVAReceiveCompleteCallback(::onEvaComplete)
    }

    fun printInfo(): Int {
        val peers = evaCommunity.getPeers()
        return peers.size
    }

    fun test() {
        if (evaCommunity.getPeers().isEmpty()) {
            return
        }

        val testPayload = ByteArray(10000000)
        testPayload.fill(42)

        val recipient = evaCommunity.getPeers().first()
        // Both community and id are necessary.
        evaCommunity.evaSendBinary(recipient, evaCommunity.serviceId, java.util.UUID.randomUUID().toString(), testPayload)

        logger.info { "Sent packets!" }
    }

    private fun onEvaProgress(peer: Peer, info: String, progress: TransferProgress) {
        if (progress.progress == 0.0) {
            evaCommunity.startTimer()
        }

    }

    private fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        evaCommunity.stopTimer(data!!.size)
    }

    private fun createEvaCommunity(): OverlayConfiguration<EvaCommunity> {
        return OverlayConfiguration(
            EvaCommunity.Factory(),
            listOf(RandomWalk.Factory(timeout = 3.0, peers = 1))
        )
    }
}