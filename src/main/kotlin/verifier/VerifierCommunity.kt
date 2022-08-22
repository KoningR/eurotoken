package verifier

import EuroCommunity
import RecipientPair
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer

class VerifierCommunity : EuroCommunity() {
    private val tokens = mutableMapOf<TokenId, Token>()

    internal fun info() {
        logger.info { getPeers().size }
    }

    internal fun createAndSend(receiver: Peer, amount: Int) {
        val newTokens = mutableSetOf<Token>()

        repeat(amount) {
            val token = Token.create(0b1, myPublicKey)
            signByVerifier(token, token.genesisHash, receiver.publicKey.keyToBin())

            tokens[TokenId(token.id)] = token

            newTokens.add(token)
        }

        send(receiver, newTokens)

        logger.info { "Create $amount new tokens!" }
    }

    internal fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {

        // TODO: Encrypt the entire history with the verifier's public key
        //  such that individuals cannot see it and privacy is maintained better?
        //  Also this removes the need to chain hashes because simple integer counters
        //  can then be used.

        // TODO: What if person keeps paying with the unverified version of a token
        //  after having it verified?

        // TODO: Maybe add all transactions including the genesis transaction to the
        //  recipients list?

        val receivedTokens = Token.deserialize(data!!)
        val verifiedTokens = mutableSetOf<Token>()

        for (receivedToken in receivedTokens) {
            if (receivedToken.numRecipients == 1) {
                logger.info { "Token is already verified!" }
                continue
            }

            if (!(myPublicKey contentEquals receivedToken.verifier)) {
                logger.info { "Token was not signed by this verifier!" }
                continue
            }

            val verifiedToken = tokens[TokenId(receivedToken.id)]
            if (verifiedToken == null) {
                logger.info { "Token ID does not exist!" }
                continue
            }

            if (verifiedToken.value != receivedToken.value) {
                logger.info { "Token has been given a different value!" }
                continue
            }

            if (!receivedToken.verifyRecipients(myPublicKey)) {
                continue
            }

            val lastVerifiedProof = verifiedToken.lastProof

            if (!(lastVerifiedProof contentEquals receivedToken.firstProof)) {
                logger.info { "Detected an attempt at double spending!" }

                findDoubleSpend(receivedToken, verifiedToken)

                continue
            }

            verifiedToken.recipients.addAll(receivedToken.recipients.subList(1, receivedToken.numRecipients))

            val lastRecipient = receivedToken.lastRecipient
            val lastProof = receivedToken.lastProof

            val newRecipientPair = signByVerifier(receivedToken, lastProof, lastRecipient)
            verifiedToken.recipients.add(newRecipientPair)

            verifiedTokens.add(receivedToken)
        }

        logger.info { "Received ${verifiedTokens.size} valid tokens and verified them!" }

        send(peer, verifiedTokens)
    }

    private fun findDoubleSpend(receivedToken: Token, realToken: Token) {

        val genesisHashOfReceivedToken = receivedToken.genesisHash

        var foundIndex = 0
        for ((i, recipientPair) in realToken.recipients.withIndex()) {
            if (recipientPair.proof contentEquals genesisHashOfReceivedToken) {
                foundIndex = i
                break
            }
        }

        val historySinceLastRedeemedProof = realToken.recipients.subList(foundIndex, realToken.numRecipients)

        for (pair in (receivedToken.recipients zip historySinceLastRedeemedProof)) {
            if (pair.first != pair.second) {
                logger.info { "Double spending verified!" }
                return
            }
        }
    }

    private fun signByVerifier(token: Token, lastVerifiedProof: ByteArray, recipient: ByteArray): RecipientPair {
        val newRecipientPair = RecipientPair(
            recipient,
            myPrivateKey.sign(token.id + token.value + lastVerifiedProof + recipient)
        )

        token.genesisHash = lastVerifiedProof
        token.recipients.clear()
        token.recipients.add(newRecipientPair)

        return newRecipientPair
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