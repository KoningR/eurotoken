package verifier

import EuroCommunity
import kotlinx.coroutines.*
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.eva.EVAProtocol
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import java.net.InetAddress

private val dispatcher = Dispatchers.IO
private val scope = CoroutineScope(dispatcher)

private lateinit var verifierCommunity: VerifierCommunity

suspend fun main() {
    withContext(Dispatchers.IO) {
        val udpEndpoint=  UdpEndpoint(9000, InetAddress.getByName("0.0.0.0"))

        val endpointAggregator = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createVerifierCommunity()
        ), walkerInterval = 1.0)

        val ipv8 = IPv8(endpointAggregator, config, Peer(Verifier.privateKey))
        ipv8.start()

        verifierCommunity = ipv8.getOverlay()!!
        verifierCommunity.evaProtocol = EVAProtocol(verifierCommunity, scope, retransmitInterval = 150L)
        verifierCommunity.evaProtocol!!.blockSize = 1200
        verifierCommunity.evaProtocol!!.windowSize = 256

        verifierCommunity.setOnEVAReceiveProgressCallback(verifierCommunity::onEvaProgress)
        verifierCommunity.setOnEVAReceiveCompleteCallback(verifierCommunity::onEvaComplete)

        verifierCommunity.endpoint.udpEndpoint?.socket?.sendBufferSize = 425984
        verifierCommunity.endpoint.udpEndpoint?.socket?.receiveBufferSize = 425984
    }

    if (EuroCommunity.DEBUG) {
        repeat(10) {
            delay(60000)
            verifierCommunity.createAndSend(verifierCommunity.getFirstPeer()!!, 40000)
        }
    }

    while (true) {
        val input = withContext(Dispatchers.IO) {
            readLine()!!
        }

        if (!executeLine(input)) {
            break
        }
    }
}

private fun executeLine(inputString: String): Boolean {
    val input = inputString.toLowerCase().split(" ")

    when (input[0]) {
        "info" -> {
            verifierCommunity.info()
        }
        "create" -> {
            verifierCommunity.createAndSend(verifierCommunity.getFirstPeer()!!,
                if (input.size == 2) input[1].toInt() else 20)
        }
    }

    return true
}

private fun createVerifierCommunity(): OverlayConfiguration<VerifierCommunity> {
    return OverlayConfiguration(
        VerifierCommunity.Factory(),
        listOf(RandomWalk.Factory(timeout = 3.0, peers = 2))
    )
}

object Verifier {
    internal val privateKey: PrivateKey = JavaCryptoProvider.keyFromPrivateBin(
        byteArrayOf(
            76, 105, 98, 78, 97, 67, 76, 83, 75, 58, -29, -114, 126, -47, -39, -5, 22, 89,
            94, 71, -1, 118, -30, 120, -8, -75, 2, 102, 99, -21, 57, -95, 124, 126, -30, 33,
            -99, 37, -125, -105, 20, -45, 94, 2, -109, 125, 98, -52, 84, -54, -47, 13, 15, 75,
            73, 11, -128, 5, -4, -101, 102, -1, -95, 33, -107, -77, -41, 89, 102, 44, 71, 107, 1, 107
        )
    )
    internal val publicKey = privateKey.pub()
}