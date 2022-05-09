import EuroCommunity.Companion.EURO_IPV8_PREFIX_SIZE
import mu.KotlinLogging
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.messaging.Packet

class Token(internal val id: ByteArray,
            internal val value: Byte,
            internal var sender: ByteArray,
            internal var receiver: ByteArray,
            internal var signature: ByteArray) {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Token

        if (!id.contentEquals(other.id)) return false

        return true
    }

    internal fun verifySenderSignature(): Boolean {
        return JavaCryptoProvider.keyFromPublicBin(sender).verify(
            signature, id + value + sender + receiver)
    }

    internal fun sign(privateKey: PrivateKey) {
        signature = privateKey.sign(id + value + sender + receiver)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        // TODO: Write a unit test to verify that these values are still correct.
        const val ID_SIZE = 8
        private const val VALUE_SIZE = 1
        private const val SENDER_SIZE = 74
        private const val RECEIVER_SIZE = 74
        private const val SIGNATURE_SIZE = 64
        internal const val TOKEN_SIZE = ID_SIZE + VALUE_SIZE + SENDER_SIZE + RECEIVER_SIZE + SIGNATURE_SIZE

        internal fun create(id: ByteArray, value: Byte, sender: ByteArray, receiver: ByteArray, privateKey: PrivateKey): Token {
            val signature = privateKey.sign(id + value + sender + receiver)
            return Token(id, value, sender, receiver, signature)
        }

        internal fun serialize(tokens: Collection<Token>, data: ByteArray) {
            // Assume data packet already has a valid prefix and a valid size.
            // Do not check how many tokens are present in the input.
            // Do not perform cryptography, simply serialize.

            var i = EURO_IPV8_PREFIX_SIZE
            for (token in tokens) {
                System.arraycopy(token.id, 0, data, i, ID_SIZE)
                i += ID_SIZE

                data[i] = token.value
                i += 1

                System.arraycopy(token.sender, 0, data, i, SENDER_SIZE)
                i += SENDER_SIZE

                System.arraycopy(token.receiver, 0, data, i, RECEIVER_SIZE)
                i += RECEIVER_SIZE

                System.arraycopy(token.signature, 0, data, i, SIGNATURE_SIZE)
                i += SIGNATURE_SIZE
            }
        }

        internal fun deserialize(packet: Packet): MutableCollection<Token> {

            val data = packet.data
            val size = data.size

            if ((size - EURO_IPV8_PREFIX_SIZE) % TOKEN_SIZE != 0) {
                logger.info { "Packet was not of the correct size!" }
                return mutableListOf()
            }

            val tokenAmount = (size - EURO_IPV8_PREFIX_SIZE) / TOKEN_SIZE
            val tokens = mutableListOf<Token>()

            var i = EURO_IPV8_PREFIX_SIZE
            repeat(tokenAmount) {
                val id = ByteArray(ID_SIZE)
                val sender = ByteArray(SENDER_SIZE)
                val receiver = ByteArray(RECEIVER_SIZE)
                val signature = ByteArray(SIGNATURE_SIZE)

                // Add a 1 for the 1 byte of the value field.
                System.arraycopy(data, i, id, 0, ID_SIZE)
                i += ID_SIZE

                val value = data[i]
                i += 1

                System.arraycopy(data, i, sender, 0, SENDER_SIZE)
                i += SENDER_SIZE

                System.arraycopy(data, i, receiver, 0, RECEIVER_SIZE)
                i += RECEIVER_SIZE

                System.arraycopy(data, i, signature, 0, SIGNATURE_SIZE)
                i += SIGNATURE_SIZE

                val token = Token(id, value, sender, receiver, signature)

                tokens.add(token)
            }

            return tokens
        }

    }
}