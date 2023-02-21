package client

import kotlinx.coroutines.*
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.eva.EVAProtocol
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import java.net.InetAddress

private val dispatcher = Dispatchers.IO
private val scope = CoroutineScope(dispatcher)

private lateinit var clientCommunity: ClientCommunity

suspend fun main() {
    withContext(dispatcher) {
        val udpEndpoint=  UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))

        val endpointAggregator = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createClientCommunity()
        ), walkerInterval = 1.0)

        val ipv8 = IPv8(endpointAggregator, config, Peer(JavaCryptoProvider.generateKey()))
        ipv8.start()

        clientCommunity = ipv8.getOverlay()!!
        clientCommunity.evaProtocol = EVAProtocol(clientCommunity, scope, retransmitInterval = 150L)
        clientCommunity.evaProtocol!!.blockSize = 1200
        clientCommunity.evaProtocol!!.windowSize = 256

        clientCommunity.setOnEVAReceiveProgressCallback(clientCommunity::onEvaProgress)
        clientCommunity.setOnEVAReceiveCompleteCallback(clientCommunity::onEvaComplete)

        clientCommunity.endpoint.udpEndpoint?.socket?.sendBufferSize = 425984
        clientCommunity.endpoint.udpEndpoint?.socket?.receiveBufferSize = 425984

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
            clientCommunity.info()
        }
        "sendverified" -> {
            clientCommunity.sendToPeer(
                clientCommunity.getFirstPeer()!!,
                if (input.size >= 2) input[1].toInt() else 1,
                true,
                input.size == 3 && input[2] == "doublespend")
        }

        "sendunverified" -> {
            clientCommunity.sendToPeer(
                clientCommunity.getFirstPeer()!!,
                if (input.size >= 2) input[1].toInt() else 1,
                false,
                input.size == 3 && input[2] == "doublespend")
        }

        "sendtobank" -> {
            clientCommunity.sendToBank(
                input.size == 2 && input[1] == "doublespend"
            )
        }
    }

    return true
}

private fun createClientCommunity(): OverlayConfiguration<ClientCommunity> {
    return OverlayConfiguration(
        ClientCommunity.Factory(),
        listOf(RandomWalk.Factory(timeout = 3.0, peers = 2))
    )
}