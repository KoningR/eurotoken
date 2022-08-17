package verifier

import EuroCommunity
import RecipientPair
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import kotlin.random.Random

class VerifierCommunity : EuroCommunity() {
    private val tokens = mutableMapOf<TokenId, Token>()

    internal fun info() {
        logger.info { getPeers().size }
    }

    internal fun createAndSend(receiver: Peer, amount: Int) {
        val newTokens = mutableSetOf<Token>()

        repeat(amount) {
            val id = Random.nextBytes(Token.ID_SIZE)
            val value: Byte = 0b1
            val token = Token.create(id, value, myPublicKey,
                receiver.publicKey.keyToBin(), myPrivateKey)

            tokens[TokenId(id)] = token
            newTokens.add(token)
        }

        send(receiver, newTokens)

        logger.info { "Create $amount new tokens!" }
    }

    internal fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        val receivedTokens = Token.deserialize(data!!)

        for (token in receivedTokens) {
            if (token.numRecipients == 1) {
                logger.info { "Token is already verified!" }
                continue
            }

            if (!(myPublicKey contentEquals token.verifier)) {
                logger.info { "Token was not signed by this verifier!" }
                continue
            }

            val oldToken = tokens[TokenId(token.id)]
            if (oldToken == null) {
                logger.info { "Token ID does not exist!" }
                continue
            }

            if (oldToken.value != token.value) {
                logger.info { "Token has been given a different value!" }
                continue
            }

            if (!token.verifyRecipients(myPublicKey)) {
                continue
            }

            val lastRecipient = token.recipients.last().publicKey
            val lastSignature = token.recipients.last().proof

            token.genesisHash = lastSignature

            token.recipients.clear()
            token.recipients.add(
                RecipientPair(
                    lastRecipient,
                    myPrivateKey.sign(token.id + token.value + lastSignature + lastRecipient)
                )
            )
            token.numRecipients = 1

            tokens[TokenId(token.id)] = token
        }

        logger.info { "Received ${receivedTokens.size} tokens and verified them!" }

        send(peer, receivedTokens)
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