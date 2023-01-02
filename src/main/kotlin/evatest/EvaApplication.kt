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
import nl.tudelft.ipv8.messaging.eva.TransferException
import nl.tudelft.ipv8.messaging.eva.TransferProgress
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import java.net.InetAddress

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
        evaCommunity.setOnEVAErrorCallback(::onEvaError)

//        evaCommunity.endpoint.udpEndpoint?.socket?.sendBufferSize = 1
//        evaCommunity.endpoint.udpEndpoint?.socket?.receiveBufferSize = 1
        logger.info { "Send buffer: ${evaCommunity.endpoint.udpEndpoint?.socket?.sendBufferSize}" }
        logger.info { "Receive buffer: ${evaCommunity.endpoint.udpEndpoint?.socket?.receiveBufferSize}" }
    }

    fun printInfo(): Int {
        val peers = evaCommunity.getPeers()
        return peers.size
    }

    fun test() {
        if (evaCommunity.getPeers().isEmpty()) {
            return
        }

        // Create a test payload of 10 megabytes.
        // Ideally, we'd use 100mb like in all previous
        // experiments, but some configurations are so
        // slow that this is too cumbersome.
        val testPayload = ByteArray(10000000)
        testPayload.fill(42)

        val recipient = evaCommunity.getPeers().first()

        repeat(10) {

            // 1400 was not an option because the maximum allowed packet
            // size is smaller than that.
            for (i in listOf(200, 600, 1000, 1200)) {
                evaCommunity.evaProtocol!!.blockSize = i

                for (j in listOf(16, 32, 64, 128, 256)) {
                    evaCommunity.evaProtocol!!.windowSize = j
                    evaCommunity.evaSendBinary(recipient, evaCommunity.serviceId, java.util.UUID.randomUUID().toString(), testPayload)
                }
            }
        }

        logger.info { "Sent packets!" }
    }

    private fun onEvaProgress(peer: Peer, info: String, progress: TransferProgress) {
        if (progress.progress == 0.0) {
            evaCommunity.startTimer(progress.id)
        }

    }

    private fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        evaCommunity.stopTimer(info.toInt(), data!!.size, id)
    }

    private fun onEvaError(peer: Peer, transferException: TransferException) {
        logger.info { "An error occurred: ${transferException.m}" }
    }

    private fun createEvaCommunity(): OverlayConfiguration<EvaCommunity> {
        return OverlayConfiguration(
            EvaCommunity.Factory(),
            listOf(RandomWalk.Factory(timeout = 3.0, peers = 1))
        )
    }
}