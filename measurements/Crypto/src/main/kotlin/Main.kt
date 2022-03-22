import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import java.lang.Exception
import kotlin.random.Random
import kotlinx.coroutines.*

// The total number of bytes to be signed and verified.
const val DATA_SIZE = 100000000
// The number of bytes of each part of the payload.
const val SPLIT_SIZE = 1492

// Values copied from the kotlin-ipv8 library.
const val SIGN_PUBLICKEY_BYTES = 32
const val SIGN_SECRETKEY_BYTES = 64
const val SIGN_SEED_BYTES = 32
const val SIGNATURE_SIZE = 64

/**
 * Convert bytes per millisecond to megabytes per second.
 */
fun throughputMbPerSecond(bytes: Int, millis: Long): Double {
    return (bytes.toDouble() / 1000000) / (millis.toDouble() / 1000)
}

fun singlePayload() {
    // Fill a single payload of DATA_SIZE bytes.
    val payload = ByteArray(DATA_SIZE)
    payload.fill(0x42)

    // Create seed and initialise arrays.
    val signSeed = Random.Default.nextBytes(SIGN_SEED_BYTES)
    val verifyKey = ByteArray(SIGN_PUBLICKEY_BYTES)
    val signKey = ByteArray(SIGN_SECRETKEY_BYTES)

    // Fill key arrays.
    val lazySodium = LazySodiumJava(SodiumJava())
    if (!lazySodium.cryptoSignSeedKeypair(verifyKey, signKey, signSeed)) {
        throw Exception("Could not create keys")
    }

    // Start timing the signing process.
    val startSignTime = System.currentTimeMillis()

    // Sign the payload.
    val signature = ByteArray(SIGNATURE_SIZE)
    if (!lazySodium.cryptoSignDetached(signature, payload,
            payload.size.toLong(), signKey)) {
        throw Exception("Could not sign payload")
    }

    // Stop timing the signing process.
    val signTime = System.currentTimeMillis() - startSignTime

    // Start timing the verification process.
    val startVerifyTime = System.currentTimeMillis()

    // Verify the payload.
    if (!lazySodium.cryptoSignVerifyDetached(signature, payload,
            payload.size, verifyKey)) {
        throw Exception("Could not verify payload")
    }

    // Stop timing the verification process.
    val verifyTime = System.currentTimeMillis() - startVerifyTime

    println("Signing payload took $signTime milliseconds")
    println("Signing throughput was ${throughputMbPerSecond(DATA_SIZE, signTime)} megabytes per second")
    println("Verifying payload took $verifyTime milliseconds")
    println("Verification throughput was ${throughputMbPerSecond(DATA_SIZE, verifyTime)} megabytes per second")
    println("Resulting signature: ${lazySodium.sodiumBin2Hex(signature)}")
}

suspend fun splitPayload() {
    // Array may hold slightly fewer bytes than DATA_SIZE due to floor division.
    val payLoadArray = Array(DATA_SIZE.floorDiv(SPLIT_SIZE)) {
        val payload = ByteArray(SPLIT_SIZE)
        payload.fill(0x42)
        payload
    }

    // Create seed and initialise arrays.
    val signSeed = Random.Default.nextBytes(SIGN_SEED_BYTES)
    val verifyKey = ByteArray(SIGN_PUBLICKEY_BYTES)
    val signKey = ByteArray(SIGN_SECRETKEY_BYTES)

    // Fill key arrays.
    val lazySodium = LazySodiumJava(SodiumJava())
    if (!lazySodium.cryptoSignSeedKeypair(verifyKey, signKey, signSeed)) {
        throw Exception("Could not create keys")
    }

    // Start timing the signing process.
    val startSignTime = System.currentTimeMillis()

    // Create a signature array for each part of the payload.
    val signatureArray = Array(payLoadArray.size) {
        ByteArray(SIGNATURE_SIZE)
    }

    withContext(Dispatchers.Default) {
        // Sign each part of the payload.
        repeat(payLoadArray.size) {
            launch {
                if (!lazySodium.cryptoSignDetached(signatureArray[it], payLoadArray[it],
                            SPLIT_SIZE.toLong(), signKey)) {
                    throw Exception("Could not sign payload")
                }
            }
        }
    }

    // Stop timing the signing process.
    val signTime = System.currentTimeMillis() - startSignTime

    // Start timing the verification process.
    val startVerifyTime = System.currentTimeMillis()

    withContext(Dispatchers.Default) {
        // Verify each part of the payload.
        repeat(signatureArray.size) {
            launch {
                if (!lazySodium.cryptoSignVerifyDetached(signatureArray[it], payLoadArray[it],
                        SPLIT_SIZE, verifyKey)) {
                    throw Exception("Could not verify payload")
                }
            }
        }
    }

    // Stop timing the verification process.
    val verifyTime = System.currentTimeMillis() - startVerifyTime

    // To make all parts of the payload the same length, the throughput
    // might be a bit smaller than DATA_SIZE.
    val actualDataSize = DATA_SIZE.floorDiv(SPLIT_SIZE) * SPLIT_SIZE

    println("Split data into ${signatureArray.size} parts of $SPLIT_SIZE bytes each")
    println("Signing payload took $signTime milliseconds")
    println("Signing throughput was ${throughputMbPerSecond(actualDataSize, signTime)} megabytes per second")
    println("Verifying payload took $verifyTime milliseconds")
    println("Verification throughput was ${throughputMbPerSecond(actualDataSize, verifyTime)} megabytes per second")
}

fun main() {
    singlePayload()

    runBlocking {
        splitPayload()
    }
}