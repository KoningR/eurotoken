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

        // If you add the last transaction to the list, the in-memory
        // object will get updated as well. As per protocol, the
        // verifier should not store this, and thus we remove it
        // afterwards.
        newTokens.forEach {
            it.recipients.removeLast()
            it.numRecipients = 0
        }

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

            val lastRedeemedProof = if (oldToken.numRecipients == 0) {
                oldToken.genesisHash
            } else {
                oldToken.recipients.last().proof
            }

            if (!(lastRedeemedProof contentEquals token.genesisHash)) {
                logger.info { "Detected an attempt at double spending!" }

                findDoubleSpend(token, oldToken)

                continue
            }

            oldToken.recipients.addAll(token.recipients)
            oldToken.numRecipients += token.recipients.size

            val lastRecipient = token.recipients.last().publicKey
            val lastProof = token.recipients.last().proof

            signByVerifier(token, lastProof, lastRecipient)

            verifiedTokens.add(token)
        }

        logger.info { "Received ${verifiedTokens.size} valid tokens and verified them!" }

        send(peer, verifiedTokens)
    }

    private fun findDoubleSpend(receivedToken: Token, realToken: Token) {

//        val genesisHashOfReceivedToken = receivedToken.genesisHash
//
//        var foundIndex = 0
//        for ((i, recipientPair) in realToken.recipients.withIndex()) {
//            if (recipientPair.proof contentEquals genesisHashOfReceivedToken) {
//                foundIndex = i
//                break
//            }
//        }
//
//        val alignedHistory = realToken.recipients.subList(foundIndex, realToken.recipients.size)
//        for (pair in (receivedToken.recipients zip alignedHistory)) {
//            if (pair.first != pair.second) {
//                logger.info { "Double spending detected!" }
//            }
//        }
    }

    private fun signByVerifier(token: Token, lastRedeemedHash: ByteArray, recipient: ByteArray) {
        token.genesisHash = lastRedeemedHash
        token.recipients.clear()
        // Note that the verifier does not store this last signature.
        token.recipients.add(
            RecipientPair(
                recipient,
                myPrivateKey.sign(token.id + token.value + lastRedeemedHash + recipient)
            )
        )
        token.numRecipients = 1
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