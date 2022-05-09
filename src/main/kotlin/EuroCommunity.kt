import client.ClientCommunity
import mu.KotlinLogging
import nl.tudelft.ipv8.Community
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.PrivateKey
import verifier.Verifier
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketAddress
import kotlin.math.min

open class EuroCommunity : Community() {
    override val serviceId: String
        get() = "381a9685c1912a141279f8222193db58u9c5duck"

    internal val logger = KotlinLogging.logger {}

    private val socket: DatagramSocket by lazy { endpoint.udpEndpoint!!.socket!! }

    internal val myPrivateKey: PrivateKey by lazy { myPeer.key as PrivateKey }
    internal val myPublicKey: ByteArray by lazy { myPeer.publicKey.keyToBin() }

    override fun getWalkableAddresses(): List<IPv4Address> {
        TODO("Fill in your own list of local IP addresses.")
//        return emptyList()
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

    internal fun send(receiverAddress: SocketAddress, tokens: MutableCollection<Token>) {
        // Assume tokens are already 'prepared' i.e. variables need not be changed.
        // Simply a helper method for mass serialization and sending.

        val packet = ByteArray(EURO_MAX_PACKET_SIZE)
        System.arraycopy(prefix, 0, packet, 0, prefix.size)
        packet[prefix.size] = ClientCommunity.EURO_CLIENT_MESSAGE

        var unsentAmount = tokens.size
        while (unsentAmount > 0) {
            // Send as many tokens as possible per packet.
            val sendAmount = min(unsentAmount, EURO_MAX_TOKENS_PER_PACKET)

            val tokensInPacket = tokens.take(sendAmount)

            Token.serialize(tokensInPacket, packet)
            socket.send(DatagramPacket(packet, 0,
                EURO_IPV8_PREFIX_SIZE + sendAmount * Token.TOKEN_SIZE, receiverAddress))

            tokens.removeAll(tokensInPacket)
            unsentAmount -= sendAmount
        }
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