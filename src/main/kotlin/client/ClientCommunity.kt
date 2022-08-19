package client

import EuroCommunity
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import verifier.Verifier

class ClientCommunity : EuroCommunity() {
    private val verifierAddress: Peer by lazy { getVerifier()!! }
    private val verifierKey = Verifier.publicKey.keyToBin()

    private val unverifiedTokens: MutableSet<Token> = mutableSetOf()
    private val verifiedTokens: MutableSet<Token> = mutableSetOf()

    internal fun info() {
        logger.info { getPeers().size }
    }

    internal fun sendToPeer(receiver: Peer, amount: Int, verified: Boolean, doubleSpend: Boolean = false) {
        val tokens =  if (verified) verifiedTokens else unverifiedTokens

        if (tokens.size < amount) {
            logger.info { "Insufficient balance!" }
            return
        }

        val tokensToSend = tokens.take(amount).toMutableSet()
        for (token in tokensToSend) {
            token.signByPeer(receiver.publicKey.keyToBin(), myPrivateKey)
        }

        if (!doubleSpend) {
            tokens.removeAll(tokensToSend)
        }

        send(receiver, tokensToSend)

        logger.info { "New verified balance: ${verifiedTokens.size}" }
        logger.info { "New unverified balance: ${unverifiedTokens.size}" }
    }

    internal fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        val receivedTokens = Token.deserialize(data!!)

        for (token in receivedTokens) {

            if (!(token.recipients.last().publicKey contentEquals myPublicKey)) {
                logger.info { "Received a token that was meant for someone else!" }
                continue
            }

            if (!token.verifyRecipients(verifierKey)) {
                continue
            }

            // If the token is completely verified and it has 1 recipient, namely this object,
            // then it must have been sent directly from a verifier and is therefore a verified
            // token.
            if (token.numRecipients == 1) {
                verifiedTokens.add(token)
            } else {
                unverifiedTokens.add(token)
            }
        }

        logger.info { "New verified balance: ${verifiedTokens.size}" }
        logger.info { "New unverified balance: ${unverifiedTokens.size}" }
    }

    internal fun sendToBank() {
        if (unverifiedTokens.isEmpty()) {
            logger.info { "There are no unverified tokens!" }
            return
        }

        send(verifierAddress, unverifiedTokens)
        unverifiedTokens.clear()

        logger.info { "Sent unverified tokens to a verifier!" }
    }

    private fun getVerifier(): Peer? {
        if (getPeers().isEmpty()) {
            return null
        }

        for (peer in getPeers()) {
            if (peer.publicKey.keyToBin() contentEquals verifierKey) {
                return peer
            }
        }

        return null
    }

    class Factory : Overlay.Factory<ClientCommunity>(ClientCommunity::class.java) {
        override fun create(): ClientCommunity {
            return ClientCommunity()
        }
    }
}
