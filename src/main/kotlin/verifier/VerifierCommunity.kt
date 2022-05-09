package verifier

import EuroCommunity
import Token
import client.ClientCommunity
import nl.tudelft.ipv8.IPv4Address
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.random.Random

class VerifierCommunity : EuroCommunity() {
    private val socket: DatagramSocket by lazy { endpoint.udpEndpoint!!.socket!! }

    private val tokens = mutableMapOf<TokenId, Token>()

    init {
        messageHandlers[ClientCommunity.EURO_CLIENT_MESSAGE.toInt()] = ::receive
    }

    internal fun info() {
        logger.info { getPeers().size }
    }

    internal fun createAndSend(receiver: Peer, amount: Int) {
        val newTokens = mutableListOf<Token>()

        repeat(amount) {
            val id = Random.nextBytes(Token.ID_SIZE)
            val value: Byte = 0b1
            val token = Token.create(id, value, myPublicKey,
                receiver.publicKey.keyToBin(), myPrivateKey)

            tokens[TokenId(id)] = token
            newTokens.add(token)
        }

        send(receiver.address.toSocketAddress(), newTokens)

        logger.info { "Create $amount new tokens!" }
    }

    private fun receive(packet: Packet) {
        val receivedTokens = Token.deserialize(packet)
        val verifiedTokens = mutableListOf<Token>()

        val iter = receivedTokens.iterator()
        while (iter.hasNext()) {
            val receivedToken = iter.next()

            if (!receivedToken.verifySenderSignature()) {
                logger.info { "Received a token that was not signed by its proclaimed sender!" }
                iter.remove()
                continue
            }

            val oldToken = tokens[TokenId(receivedToken.id)]
            if (oldToken == null) {
                logger.info { "Received a token ID that does not exist!" }
                iter.remove()
                continue
            }

            if (oldToken.value != receivedToken.value) {
                logger.info { "Received a token with a differing value!" }
                iter.remove()
                continue
            }

            if (!(oldToken.receiver contentEquals receivedToken.sender)) {
                logger.info { "Received a token that was not signed by its owner!" }
                iter.remove()
                continue
            }

            // Update the last known entry of the token.
            oldToken.receiver = receivedToken.receiver
            oldToken.sign(myPrivateKey)

            verifiedTokens.add(oldToken)
        }

        Token.serialize(verifiedTokens, packet.data)
        socket.send(DatagramPacket(packet.data, 0,
            EURO_IPV8_PREFIX_SIZE + verifiedTokens.size * Token.ID_SIZE,
            (packet.source as IPv4Address).toSocketAddress()))

        logger.info { "Received ${verifiedTokens.size} tokens and verified them!" }
    }

    class Factory : Overlay.Factory<VerifierCommunity>(VerifierCommunity::class.java) {
        override fun create(): VerifierCommunity {
            return VerifierCommunity()
        }
    }

    /**
    A wrapper class of ByteArray that implements its own equals() and hashCode()
    methods. Reason is that Kotlin's HashMap only checks for pointer equality of
    arrays and not for array contents. Therefore, deserialized tokens would never
    be equal to tokens already in memory without this class.
     */
    class TokenId(private val id: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TokenId

            if (!id.contentEquals(other.id)) return false

            return true
        }

        override fun hashCode(): Int {
            return id.contentHashCode()
        }
    }
}