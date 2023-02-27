import java.io.File
import kotlin.random.Random

const val TOTAL_BYTES = 100000000

private data class Signature(val data: ByteArray, val signature: ByteArray)

private fun megabytesPerSecond(bytes: Int, elapsedTime: Long): Double {
    return (bytes / 1000000.toDouble())  / (elapsedTime / 1000000000.toDouble())
}

private fun bulkVerify(chunkSize: Int): Pair<Double, Double> {
    // Fill a list with chunks of data of chunkSize bytes long,
    // along with their respective signature.
    val signatureList = mutableListOf<Signature>()

    var cumulatedBytes = 0
    val data = Random.nextBytes(chunkSize)

    // Warmup round.
    Crypto.sign(data)

    // Start measuring signing.
    val signStartTime = System.nanoTime()
    while (cumulatedBytes < TOTAL_BYTES) {

        // Sign the verifier's payload.
        val signature = Crypto.sign(data)

        signatureList.add(Signature(data, signature))

        cumulatedBytes += chunkSize
    }

    // Stop measuring signing.
    val signEndTime = System.nanoTime()

    // Warmup round.
    Crypto.verify(signatureList.first().signature,
        signatureList.first().data)

    // Start measuring verification.
    val verifyStartTime = System.nanoTime()

    for (signature in signatureList) {
        Crypto.verify(signature.signature, signature.data)
    }

    // Stop measuring verification.
    val verifyEndTime = System.nanoTime()

    val signTime = megabytesPerSecond(cumulatedBytes, signEndTime - signStartTime)
    val verifyTime = megabytesPerSecond(cumulatedBytes, verifyEndTime - verifyStartTime)

    return Pair(signTime, verifyTime)
}

fun main() {

    val numRepetitions = 100

    // Concatenate the result to .csv format.
    var signResultString = "size,throughput\n"
    var verifyResultString = "size,throughput\n"

    repeat(numRepetitions) {

        for (i in 20 downTo 1) {
            val chunkSize = 128 * i * i

            val measurement = bulkVerify(chunkSize)

            signResultString += "$chunkSize,${measurement.first}\n"
            verifyResultString += "$chunkSize,${measurement.second}\n"

            println("Throughput for $chunkSize bytes is ${measurement.first} for signing " +
                    "and ${measurement.second} for verification.")
        }
    }

    File("Achievable Cryptographic Signing Throughput.csv").writeText(signResultString)
    File("Achievable Cryptographic Verification Throughput.csv").writeText(verifyResultString)
}
