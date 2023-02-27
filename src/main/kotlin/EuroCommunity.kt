import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PrivateKey
import verifier.Verifier

open class EuroCommunity : Community() {
    override val serviceId: String
        get() = "381a9685c1912a141279f8222193db58u9c5duck"

    internal val logger = KotlinLogging.logger {}

    internal val myPrivateKey: PrivateKey by lazy { myPeer.key as PrivateKey }
    internal val myPublicKey: ByteArray by lazy { myPeer.publicKey.keyToBin() }

    // EVA requires an ID per transaction.
    private var sendCounter = 0

    internal var startReceiveTime = -1L

    override fun getWalkableAddresses(): List<IPv4Address> {
        return listOf(
            IPv4Address("127.0.0.1", 8090),
            IPv4Address("127.0.0.1", 8091)
        )
    }

    internal fun info() {
        val sendBufferSize = endpoint.udpEndpoint?.socket?.sendBufferSize
        val receiveBufferSize = endpoint.udpEndpoint?.socket?.receiveBufferSize

        logger.info { "Number of peers: ${getPeers().size}" }
        logger.info { "Send buffer: $sendBufferSize Receive buffer: $receiveBufferSize" }
    }

    internal fun getFirstPeer(): Peer? {
        if (getPeers().isEmpty()) {
            return null
        }

        for (peer in getPeers()) {
            if (!(peer.publicKey.keyToBin() contentEquals Verifier.publicKey.keyToBin())
                && !(peer.publicKey.keyToBin() contentEquals myPublicKey)) {
                return peer
            }
        }

        return null
    }

    internal fun send(receiver: Peer, tokens: MutableSet<Token>) {
        // Assume tokens are already 'prepared' i.e. variables need not be changed.
        // Simply a helper method for mass serialization and sending.

        val data = Token.serialize(tokens)

        evaSendBinary(receiver, serviceId, sendCounter++.toString(), data)
    }

    companion object {
        internal const val DEBUG = false
    }
}