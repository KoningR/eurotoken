package client

import EuroCommunity
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.Packet
import verifier.Verifier

class ClientCommunity : EuroCommunity() {
    private val verifierAddress: Peer by lazy { getVerifier()!! }
    private val verifierKey = Verifier.publicKey.keyToBin()

    private val unverifiedTokens: MutableSet<Token> = mutableSetOf()
    private val verifiedTokens: MutableSet<Token> = mutableSetOf()

    init {
        messageHandlers[EURO_CLIENT_MESSAGE.toInt()] = ::receive
    }

    internal fun info() {
        logger.info { getPeers().size }
    }

    internal fun sendToPeer(receiver: Peer, amount: Int) {
        if (verifiedTokens.size < amount) {
            logger.info { "Insufficient balance!" }
            return
        }

        val tokensToSend = verifiedTokens.take(amount).toMutableList()
        for (token in tokensToSend) {
            token.sender = myPublicKey
            token.receiver = receiver.publicKey.keyToBin()
            token.sign(myPrivateKey)
        }

        verifiedTokens.removeAll(tokensToSend)

        send(receiver.address.toSocketAddress(), tokensToSend)
    }

    private fun sendToBank() {
        // TODO: Calls to toSocketAddress() might be very slow.
        send(verifierAddress.address.toSocketAddress(), unverifiedTokens)
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

    private fun receive(packet: Packet) {
        val receivedTokens = Token.deserialize(packet)
        for (token in receivedTokens) {

            if (!(token.receiver contentEquals myPublicKey)) {
                logger.info { "Received a token that was meant for someone else!" }
                continue
            }

            if (!token.verifySenderSignature()) {
                logger.info { "Received a token that was not signed by its proclaimed sender!" }
                continue
            }

            if (token.sender contentEquals verifierKey) {
                verifiedTokens.add(token)
            } else {
                unverifiedTokens.add(token)
            }
        }

        logger.info { "New verified balance: ${verifiedTokens.size}" }
        logger.info { "New unverified balance: ${unverifiedTokens.size}" }

        sendToBank()
    }

    companion object MessageId {
        const val EURO_CLIENT_MESSAGE: Byte = 0b111
    }

    class Factory : Overlay.Factory<ClientCommunity>(ClientCommunity::class.java) {
        override fun create(): ClientCommunity {
            return ClientCommunity()
        }
    }
}
