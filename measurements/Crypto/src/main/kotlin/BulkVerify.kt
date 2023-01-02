import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import java.io.File
import java.lang.Exception
import kotlin.random.Random

const val TOTAL_BYTES = 100000000

private data class Signature(val data: ByteArray, val signature: ByteArray)

private fun bulkVerify(chunkSize: Int): Double {
    // Create seed and arrays.
    val signSeed = Random.Default.nextBytes(SIGN_SEED_BYTES)
    val publicKey = ByteArray(SIGN_PUBLICKEY_BYTES)
    val secretKey = ByteArray(SIGN_SECRETKEY_BYTES)

    // Generate keypair.
    val lazySodium = LazySodiumJava(SodiumJava())
    if (!lazySodium.cryptoSignSeedKeypair(publicKey, secretKey, signSeed)) {
        throw Exception("Could not create keys!")
    }

    // Fill a list with chunks of data of chunkSize bytes long,
    // along with their respective signature.
    val signatureList = mutableListOf<Signature>()

    var cumulatedBytes = 0
    while (cumulatedBytes < TOTAL_BYTES) {

        // Sign the verifier's payload.
        val data = Random.Default.nextBytes(chunkSize)
        val signature = ByteArray(SIGNATURE_SIZE)

        if (!lazySodium.cryptoSignDetached(signature, data,
                data.size.toLong(), secretKey)) {
            throw Exception("Could not sign verifier payload!")
        }

        signatureList.add(Signature(data, signature))

        cumulatedBytes += chunkSize
    }

    // Start measuring.
    val startTime = System.nanoTime()

    for (signature in signatureList) {
        // Verify signature.
        if (!lazySodium.cryptoSignVerifyDetached(signature.signature, signature.data,
                signature.data.size, publicKey)) {
            throw Exception("Could not verify verifier signature!")
        }
    }

    // Stop measuring.
    val endTime = System.nanoTime()

    return (cumulatedBytes / 1000000.toDouble())  / ((endTime - startTime) / 1000000000.toDouble())
}

fun main() {

    val numRepetitions = 20

    // Concatenate the result to .csv format.
    var resultString = "size,throughput\n"

    repeat(numRepetitions) {

        for (i in 20 downTo 1) {
            val chunkSize = 128 * i * i

            resultString += "$chunkSize,${bulkVerify(chunkSize)}\n"

            println("Throughput for $chunkSize bytes is ${bulkVerify(chunkSize)}")
        }
    }

    File("Achievable Cryptographic Verification Throughput.csv").writeText(resultString)
}
