package verifier

import EuroCommunity
import RecipientPair
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.eva.TransferProgress
import nl.tudelft.ipv8.util.toHex
import java.io.File
import java.nio.charset.Charset

class VerifierCommunity : EuroCommunity() {
    private val tokens = mutableMapOf<TokenId, Token>()
    
    internal fun createAndSend(receiver: Peer, amount: Int) {
        val newTokens = mutableSetOf<Token>()

        // Mint new tokens.
        repeat(amount) {
            val token = Token.create(0b1, myPublicKey)
            tokens[TokenId(token.id)] = token
            newTokens.add(token)
        }

        val startTime = System.nanoTime()

        // Sign the tokens.
        for (token in newTokens) {
            signByVerifier(token, token.genesisHash, receiver.publicKey.keyToBin())
        }

        // Send the tokens.
        send(receiver, newTokens)

        val endTime = System.nanoTime()

        logger.info { "Created $amount new tokens!" }

        if (DEBUG) {
            val tokensPerSecond = amount / ((endTime - startTime).toDouble() / 1000000000)
            File("TokenAuthoritySign.txt").appendText("$tokensPerSecond,\n", Charset.defaultCharset())

            logger.info { "Throughput of signing and serialization was: $tokensPerSecond" }
        }
    }

    internal fun onEvaProgress(peer: Peer, info: String, progress: TransferProgress) {
        if (startReceiveTime < 0) {
            startReceiveTime = System.nanoTime()
            logger.info { "Starting EVA transaction..." }
        }
    }

    internal fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        val endReceiveTime = System.nanoTime()

        val receivedTokens = Token.deserialize(data!!)
        val verifiedTokens = mutableSetOf<Token>()

        for (receivedToken in receivedTokens) {
            // A token needs at least 3 recipients before it makes
            // sense to verify; the initial recipient, the second
            // one, and then back to the authority.
            if (receivedToken.numRecipients < 3) {
                logger.info { "Token is already verified!" }
                continue
            }

            if (!(myPublicKey contentEquals receivedToken.lastRecipient)) {
                logger.info { "Token was not intended for this authority!" }
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

            // Drop the last recipient, because it is the authority
            // itself.
            receivedToken.recipients.removeLast()

            val lastVerifiedProof = verifiedToken.lastProof

            if (!(lastVerifiedProof contentEquals receivedToken.firstProof)) {
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

        val endVerifyTime = System.nanoTime()

        logger.info { "Received ${verifiedTokens.size} valid tokens and verified them!" }

        if (DEBUG) {
            val receiveTokensPerSecond = receivedTokens.size / ((endReceiveTime - startReceiveTime).toDouble() / 1000000000)
            val verifyTokensPerSecond = receivedTokens.size / ((endVerifyTime - endReceiveTime).toDouble() / 1000000000)

            startReceiveTime = -1L

            File("TokenAuthorityReceive.txt").appendText("$receiveTokensPerSecond,\n", Charset.defaultCharset())
            File("TokenAuthorityVerify.txt").appendText("$verifyTokensPerSecond,\n", Charset.defaultCharset())
        } else {
            send(peer, verifiedTokens)
        }
    }

    private fun findDoubleSpend(receivedToken: Token, verifiedToken: Token) {

        val receivedFirstProof = receivedToken.firstProof

        // The first proof of the received token must exist somewhere in the history
        // of the verified token. This is because we have already verified the token's
        // proof chain, which requires that its first proof is signed by the verifier itself.
        // Therefore, the verifier must have a record of it.
        // It is also not possible for indexOfReceivedProof to point
        // to the last element in the list. If it were, then the first
        // proof of the received token would be equal to the last proof
        // of the verified token, which only happens when there is no
        // double spending or when it cannot yet be detected. Thus, this
        // function would have never been called.
        var indexOfReceivedProof = 0
        for ((i, recipientPair) in verifiedToken.recipients.withIndex()) {
            if (recipientPair.proof contentEquals receivedFirstProof) {
                indexOfReceivedProof = i
                break
            }
        }

        val historySinceReceivedProof = verifiedToken.recipients.subList(
            indexOfReceivedProof,
            verifiedToken.numRecipients)

        // In the first iteration of this loop, the compared pair will always
        // be identical.
        var doubleSpender = receivedToken.firstRecipient
        for (pair in (receivedToken.recipients zip historySinceReceivedProof)) {
            if (pair.first.proof contentEquals pair.second.proof) {
                doubleSpender = pair.first.publicKey
            } else {
                val doubleSpenderName = JavaCryptoProvider.keyFromPublicBin(doubleSpender).keyToHash().toHex()
                logger.info { "$doubleSpenderName attempted to double spend!" }

                return
            }
        }

        if (receivedToken.numRecipients != historySinceReceivedProof.size) {
            val doubleSpenderName = JavaCryptoProvider.keyFromPublicBin(doubleSpender).keyToHash().toHex()
            logger.info { "$doubleSpenderName had already redeemed their tokens" +
                    " and continued spending them!" }

            return
        }

        logger.info { "The double spending detection failed!" }
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