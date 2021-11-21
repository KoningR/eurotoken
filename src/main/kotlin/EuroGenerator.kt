import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.keyvault.PrivateKey
import nl.tudelft.ipv8.keyvault.PublicKey
import kotlin.random.Random

class EuroGenerator {
    companion object {
        private const val TOKEN_NONCE_SIZE = 128
        private val TOKEN_STRING = "TOKEN".toByteArray(Charsets.US_ASCII)
        private val TOKEN_STRING_SIZE = TOKEN_STRING.size


//        private val privateKey: PrivateKey = JavaCryptoProvider.generateKey()
        private val privateKey: PrivateKey = JavaCryptoProvider.keyFromPrivateBin(
            byteArrayOf(
                76, 105, 98, 78, 97, 67, 76, 83, 75, 58, -29, -114, 126, -47, -39, -5, 22, 89,
                94, 71, -1, 118, -30, 120, -8, -75, 2, 102, 99, -21, 57, -95, 124, 126, -30, 33,
                -99, 37, -125, -105, 20, -45, 94, 2, -109, 125, 98, -52, 84, -54, -47, 13, 15, 75,
                73, 11, -128, 5, -4, -101, 102, -1, -95, 33, -107, -77, -41, 89, 102, 44, 71, 107, 1, 107
            )
        )

        private val publicKey: PublicKey = privateKey.pub()

        fun generateToken(): ByteArray {
            var token = TOKEN_STRING + Random.nextBytes(TOKEN_NONCE_SIZE)
            token += privateKey.sign(token)
            return token
        }

        fun verifyToken(token: ByteArray): Boolean {

            val totalSize = TOKEN_NONCE_SIZE + TOKEN_STRING_SIZE + 64
            val endOfNonceIndex = TOKEN_NONCE_SIZE + TOKEN_STRING_SIZE - 1

            // TODO: Switch these around.

            return token.size == totalSize
                    && token.sliceArray(TOKEN_STRING.indices).contentEquals(TOKEN_STRING)
                    && publicKey.verify(token.sliceArray(endOfNonceIndex + 1 until totalSize),
                                        token.sliceArray(0..endOfNonceIndex))
        }
    }
}