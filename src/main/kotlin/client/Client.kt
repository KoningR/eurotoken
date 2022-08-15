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
    // TODO
    //  Implement offline mode.
    //  Implement a persistence mechanism.
    //  Implement threading and coroutines.
    //  Implement an acknowledgement system.
    //  Create separate databases for unacknowledged tokens.
    //  Perform optimisations:
    //      Use another walking mechanism / peer discovery.
    //      Change the crypto API to one that supports indexing.
    //      Initialise packets statically.
    //      Replace calls of toSocketAddress().

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
        clientCommunity.setOnEVAReceiveCompleteCallback(clientCommunity::onEvaComplete)
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
        "send" -> {
            clientCommunity.sendToPeer(clientCommunity.getFirstPeer()!!,
                if (input.size == 2) input[1].toInt() else 2)
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