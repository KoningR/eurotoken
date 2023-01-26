package client

import EuroCommunity
import Token
import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.messaging.eva.TransferProgress
import verifier.Verifier
import java.io.File
import java.nio.charset.Charset

class ClientCommunity : EuroCommunity() {
    private val verifierAddress: Peer by lazy { getVerifier()!! }
    private val verifierKey = Verifier.publicKey.keyToBin()

    private val unverifiedTokens: MutableSet<Token> = mutableSetOf()
    private val verifiedTokens: MutableSet<Token> = mutableSetOf()

    private var startReceiveTime = -1L

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

        send(receiver, tokensToSend)

        if (doubleSpend) {
            tokensToSend.forEach {
                it.recipients.removeLast()
            }
        } else {
            tokens.removeAll(tokensToSend)
        }

        logger.info { "New verified balance: ${verifiedTokens.size}" }
        logger.info { "New unverified balance: ${unverifiedTokens.size}" }
    }

    internal fun sendToBank(doubleSpend: Boolean = false) {
        if (unverifiedTokens.isEmpty()) {
            logger.info { "There are no unverified tokens!" }
            return
        }

        send(verifierAddress, unverifiedTokens)

        if (!doubleSpend) {
            unverifiedTokens.clear()
        }

        logger.info { "Sent unverified tokens to a verifier!" }
    }

    internal fun onEvaProgress(peer: Peer, info: String, progress: TransferProgress) {
        if (startReceiveTime < 0) {
            startReceiveTime = System.nanoTime()
        }
    }

    internal fun onEvaComplete(peer: Peer, info: String, id: String, data: ByteArray?) {
        // TODO: Verify for fun that the received tokens are not already in possession
        //  of this client.

        val endReceiveTime = System.nanoTime()

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

        val endVerifyTime = System.nanoTime()

//        val throughput = throughputMbPerSecond(data.size, endReceiveTime - startReceiveTime)
        val receiveTokensPerSecond = receivedTokens.size / ((endReceiveTime - startReceiveTime).toDouble() / 1000000000)
        val verifyTokensPerSecond = receivedTokens.size / ((endVerifyTime - endReceiveTime).toDouble() / 1000000000)

        File("TokenClientReceive.txt").appendText("$receiveTokensPerSecond,\n", Charset.defaultCharset())
        File("TokenClientVerify.txt").appendText("$verifyTokensPerSecond,\n", Charset.defaultCharset())

        startReceiveTime = -1L

//        logger.info { "Throughput in megabytes per second: $throughput" }
        logger.info { "Tokens received per second: $receiveTokensPerSecond" }
        logger.info { "Tokens verified per second: $verifyTokensPerSecond" }
        logger.info { "New verified balance: ${verifiedTokens.size}" }
        logger.info { "New unverified balance: ${unverifiedTokens.size}" }
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
