import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import kotlinx.coroutines.*
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.KeyPairGenerator
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.File
import java.lang.Exception
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.random.Random

// Values copied from the kotlin-ipv8 library.
internal const val SIGN_SEED_BYTES = 32
internal const val SIGN_PUBLICKEY_BYTES = 32
internal const val SIGN_SECRETKEY_BYTES = 64
internal const val SIGNATURE_SIZE = 64

// Values copied from the Eurotoken library.
private const val ID_SIZE = 8
private const val VALUE_SIZE = 1

// The payloads are of the same size as the payloads verified in
// the Eurotoken library.
internal const val VERIFIER_PAYLOAD_SIZE = ID_SIZE + VALUE_SIZE + SIGNATURE_SIZE + SIGN_PUBLICKEY_BYTES
internal const val CLIENT_PAYLOAD_SIZE = SIGNATURE_SIZE + SIGN_PUBLICKEY_BYTES

internal data class Token(
    val verifierPayload: ByteArray,
    val verifierSignature: ByteArray,
    val clientPayload: ByteArray,
    val clientSignature: ByteArray
)

private class Crypto {
    enum class ALGORITHM {
        LIBSODIUM, BOUNCY_CASTLE, I2P
    }

    companion object {
        private val algorithm = ALGORITHM.LIBSODIUM

        // Seed and arrays for Libsodium.
        val naclSignSeed = Random.nextBytes(SIGN_SEED_BYTES)
        val naclPublicKey = ByteArray(SIGN_PUBLICKEY_BYTES)
        val naclSecretKey = ByteArray(SIGN_SECRETKEY_BYTES)
        val lazySodium = LazySodiumJava(SodiumJava())

        // Signer and verifier for BouncyCastle.
        val bouncySigner: Ed25519Signer
        val bouncyVerifier: Ed25519Signer

        val i2pSigner: EdDSAEngine
        val i2pVerifier: EdDSAEngine

        init {
            // Generate keypair for Libsodium.
            if (!lazySodium.cryptoSignSeedKeypair(naclPublicKey, naclSecretKey, naclSignSeed)) {
                throw Exception("Could not create keys!")
            }

            // Generate keypair for BouncyCastle.
            // Note that the bit-lengths between the two APIs vary:
            // https://crypto.stackexchange.com/questions/54353/
            // why-are-nacl-secret-keys-64-bytes-for-signing-but-32-bytes-for-box/54367#54367
            val kpg = Ed25519KeyPairGenerator()
            kpg.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val kp = kpg.generateKeyPair()
            val bouncyPublicKey = kp.public as Ed25519PublicKeyParameters
            val bouncySecretKey = kp.private as Ed25519PrivateKeyParameters

            // Initialize signer and verifier objects for Bouncy Castle.
            bouncySigner = Ed25519Signer()
            bouncySigner.init(true, bouncySecretKey)

            bouncyVerifier = Ed25519Signer()
            bouncyVerifier.init(false, bouncyPublicKey)

            // Generate keypair for I2P.
            val i2pKpg = KeyPairGenerator()
            val i2pKp = i2pKpg.generateKeyPair()
            i2pSigner = EdDSAEngine()
            i2pSigner.initSign(i2pKp.private)
            i2pVerifier = EdDSAEngine()
            i2pVerifier.initVerify(i2pKp.public)
        }

        fun sign(payload: ByteArray): ByteArray {
            when (algorithm) {
                ALGORITHM.LIBSODIUM -> {
                    val signature = ByteArray(SIGNATURE_SIZE)
                    lazySodium.cryptoSignDetached(signature, payload,
                        payload.size.toLong(), naclSecretKey)

                    return signature
                }

                ALGORITHM.I2P -> {
                    return i2pSigner.signOneShot(payload)

                }

                ALGORITHM.BOUNCY_CASTLE -> {
                    bouncySigner.update(payload, 0, payload.size)
                    val signature = bouncySigner.generateSignature()
                    bouncySigner.reset()

                    return signature
                }
            }
        }

        fun verify(signature: ByteArray, payload: ByteArray): Boolean {
            when(algorithm) {
                ALGORITHM.LIBSODIUM -> {
                    return lazySodium.cryptoSignVerifyDetached(signature, payload,
                        payload.size, naclPublicKey)
                }

                ALGORITHM.I2P -> {
                    return i2pVerifier.verifyOneShot(payload, signature)
                }

                ALGORITHM.BOUNCY_CASTLE -> {
                    bouncyVerifier.update(payload, 0, payload.size)
                    val ok = bouncyVerifier.verifySignature(signature)
                    bouncySigner.reset()

                    return ok
                }
            }
        }
    }
}

/**
 * This script measures the throughput of verifying the same cryptographic
 * material as found in 1 Eurotoken (assuming an online setting). The
 * difference between the real implementation and this script is that
 * this script has stripped away all wrappers and
 * protocol-related logic, to purely measure the performance of the
 * cryptographic operations of Libsodium's Kotlin port. This is the same
 * library used by the Eurotoken project as well as by kotlin-ipv8. The
 * sizes of the keys, signatures, etc. are identical to those used in
 * Eurotoken and kotlin-ipv8 as well.
 */
fun main() {
    val numThreads = 1
    val numRepetitions = 10

    val results = Array(numRepetitions) {
        DoubleArray(numThreads)
    }

    // Warmup round.
    runBlocking {
//        measureVerify(1000, 100, numThreads)
        measureSign(1000, 100, numThreads)
    }

    // Loop for every repetition.
    for (i in results.indices) {
        for (j in numThreads downTo 1) {

            // Execute each operation in this block sequentially
            // with the runBlocking{} keyword.
            results[i][j - 1] = runBlocking {
//                measureVerify(1000, 100, j)
                measureSign(1000, 100, j)
            }

            println("Done with iteration $i $j.")
        }
    }

    // Concatenate the result to .csv format.
    var resultString = ""
    for (longArray in results) {
        resultString += longArray.joinToString(separator = ",", postfix = "\n")
    }

    File("Achievable Token Verification Throughput.csv").writeText(resultString)
}

/**
 * Measure the signature verification throughput.
 * @param tokensPerTask The number of tasks executed per batch.
 * @param numTasks The number of batches that will be verified.
 * @param numThreads The number of threads on which the batches will be scheduled.
 * @return The number of tokens verified per second.
 */
private suspend fun measureVerify(tokensPerTask: Int, numTasks: Int, numThreads: Int): Double {
    // Create the array of 'tokens'.
    val tokenArray = Array(numTasks) {

        // Sign the verifier's payload.
        val verifierPayload = Random.Default.nextBytes(VERIFIER_PAYLOAD_SIZE)
        val verifierSignature = Crypto.sign(verifierPayload)

        // Sign the client's payload.
        val clientPayload = Random.Default.nextBytes(CLIENT_PAYLOAD_SIZE)
        val clientSignature = Crypto.sign(clientPayload)

        Token(verifierPayload, verifierSignature, clientPayload, clientSignature)
    }

    // Create a thread pool and define the maximum number of threads.
    val threadPool = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()

    // Start measuring.
    val startTime = System.nanoTime()

    // Verify in parallel.
    withContext(threadPool) {
        repeat(numTasks) {

            launch {
                val token = tokenArray[it]

                // One iteration of this loop corresponds to verifying one token.
                repeat(tokensPerTask) {

                    // Verify verifier signature.
                    if (!Crypto.verify(token.verifierSignature, token.verifierPayload)) {
                        throw Exception("Could not verify verifier signature!")
                    }

                    // Verify client signature.
                    if (!Crypto.verify(token.clientSignature, token.clientPayload)) {
                        throw Exception("Could not verify client signature!")
                    }
                }
            }

        }
    }

    // Stop measuring.
    val endTime = System.nanoTime()

    threadPool.close()

    return (1000000000.toDouble() / (endTime - startTime) * tokensPerTask * numTasks)
}

private suspend fun measureSign(tokensPerTask: Int, numTasks: Int, numThreads: Int): Double {
    // Create a thread pool and define the maximum number of threads.
    val threadPool = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()

    // Start measuring.
    val startTime = System.nanoTime()

    // Verify in parallel.
    withContext(threadPool) {
        repeat(numTasks) {

            launch {
                // One iteration of this loop corresponds to verifying one token.
                repeat(tokensPerTask) {

                    // Sign the verifier's payload.
                    val verifierPayload = ByteArray(VERIFIER_PAYLOAD_SIZE)
                    val verifierSignature = Crypto.sign(verifierPayload)

                    // Sign the client's payload.
                    val clientPayload = ByteArray(CLIENT_PAYLOAD_SIZE)
                    val clientSignature = Crypto.sign(clientPayload)
                }
            }

        }
    }

    // Stop measuring.
    val endTime = System.nanoTime()

    threadPool.close()

    return (1000000000.toDouble() / (endTime - startTime) * tokensPerTask * numTasks)
}