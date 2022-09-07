import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import kotlinx.coroutines.*
import java.lang.Exception
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

private data class Token(
    val verifierPayload: ByteArray,
    val verifierSignature: ByteArray,
    val clientPayload: ByteArray,
    val clientSignature: ByteArray
)

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
    val numThreads = 8
    val numRepetitions = 20

    val results = Array(numRepetitions) {
        DoubleArray(numThreads)
    }

    // Warmup round.
    runBlocking {
        verify(100, 100, numThreads)
    }

    for (i in results.indices) {
//        for (j in 1..numThreads) {
        for (j in numThreads downTo 1) {
            results[i][j - 1] = runBlocking {
                verify(100, 100, j)
            }

            println("Done with iteration $i $j.")
        }
    }

    // Concatenate the result to .csv format.
    var resultString = ""
    for (longArray in results) {
        resultString += longArray.joinToString(separator = ",", postfix = "\n")
    }

//    File("Achievable Token Verification Throughput.csv").writeText(resultString)
}

/**
 * Measure the signature verification throughput.
 * @param tokensPerTask The number of tasks executed per batch.
 * @param numTasks The number of batches that will be verified.
 * @param numThreads The number of threads on which the batches will be scheduled.
 * @return The number of tokens verified per second.
 */
private suspend fun verify(tokensPerTask: Int, numTasks: Int, numThreads: Int): Double {
    // Create seed and arrays.
    val signSeed = Random.Default.nextBytes(SIGN_SEED_BYTES)
    val publicKey = ByteArray(SIGN_PUBLICKEY_BYTES)
    val secretKey = ByteArray(SIGN_SECRETKEY_BYTES)

    // Generate keypair.
    val lazySodium = LazySodiumJava(SodiumJava())
    if (!lazySodium.cryptoSignSeedKeypair(publicKey, secretKey, signSeed)) {
        throw Exception("Could not create keys!")
    }

    // Create the array of 'tokens'.
    val tokenArray = Array(numTasks) {

        // Sign the verifier's payload.
        val verifierPayload = Random.Default.nextBytes(VERIFIER_PAYLOAD_SIZE)
        val verifierSignature = ByteArray(SIGNATURE_SIZE)

        if (!lazySodium.cryptoSignDetached(verifierSignature, verifierPayload,
                verifierPayload.size.toLong(), secretKey)) {
            throw Exception("Could not sign verifier payload!")
        }

        // Sign the client's payload.
        val clientPayload = Random.Default.nextBytes(CLIENT_PAYLOAD_SIZE)
        val clientSignature = ByteArray(SIGNATURE_SIZE)

        if (!lazySodium.cryptoSignDetached(clientSignature, clientPayload,
                clientPayload.size.toLong(), secretKey)) {
            throw Exception("Could not sign client payload!")
        }

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
                val publicKeyClone = publicKey.clone()

                // One iteration of this loop corresponds to verifying one token.
                repeat(tokensPerTask) {

                    // Verify verifier signature.
                    if (!lazySodium.cryptoSignVerifyDetached(token.verifierSignature, token.verifierPayload,
                            token.verifierPayload.size, publicKeyClone)) {
                        throw Exception("Could not verify verifier signature!")
                    }

                    // Verify client signature.
                    if (!lazySodium.cryptoSignVerifyDetached(token.clientSignature, token.clientPayload,
                            token.clientPayload.size, publicKeyClone)) {
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