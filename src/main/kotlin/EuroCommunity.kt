import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PrivateKey
import verifier.Verifier
import java.net.DatagramSocket

open class EuroCommunity : Community() {
    override val serviceId: String
        get() = "381a9685c1912a141279f8222193db58u9c5duck"

    internal val logger = KotlinLogging.logger {}

    internal val myPrivateKey: PrivateKey by lazy { myPeer.key as PrivateKey }
    internal val myPublicKey: ByteArray by lazy { myPeer.publicKey.keyToBin() }

    // EVA requires an ID per transaction.
    private var sendCounter = 0

    override fun getWalkableAddresses(): List<IPv4Address> {
        return listOf(
            IPv4Address("127.0.0.1", 8090),
            IPv4Address("127.0.0.1", 8091)
        )
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

    internal fun send(receiver: Peer, tokens: MutableCollection<Token>) {
        // Assume tokens are already 'prepared' i.e. variables need not be changed.
        // Simply a helper method for mass serialization and sending.

        val data = ByteArray(tokens.size * Token.TOKEN_SIZE)
        Token.serialize(tokens, data)

        evaSendBinary(receiver, serviceId, sendCounter++.toString(), data)

        // Remove tokens that were sent.
        tokens.clear()
    }

    companion object {
        // Max packet size of a UDP Eurotoken payload in bytes.
        // The first EURO_IPV8_PREFIX_SIZE bytes are filled with
        // values necessary for IPv8, namely the community.prefix
        // and 1 byte for the community.messageId.
        const val EURO_MAX_PACKET_SIZE = 1500
        const val EURO_IPV8_PREFIX_SIZE = 23

        // The maximum number of tokens per packet.
        const val EURO_MAX_TOKENS_PER_PACKET = (EURO_MAX_PACKET_SIZE - EURO_IPV8_PREFIX_SIZE) / Token.TOKEN_SIZE
    }
}