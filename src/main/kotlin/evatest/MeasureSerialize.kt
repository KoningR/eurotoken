package evatest

import nl.tudelft.ipv8.messaging.eva.decodeToIntegerList
import nl.tudelft.ipv8.messaging.eva.encodeToByteArray
import java.lang.ArithmeticException
import kotlin.random.Random

// https://stackoverflow.com/questions/54187695/median-calculation-in-kotlin
fun median(list: List<Long>) = list.sorted().let {
    if (it.size % 2 == 0)
        (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
    else
        it[it.size / 2]
}

fun measureSerialize(): Pair<Long, Long> {
    // Create a list of 300 random integers.
    // This will become an array of 1200 bytes, which
    // is approximately the payload of a UDP packet
    // in the EVA protocol.
    val ints = (1..300).map { Random.nextInt() }

    val startSerialize = System.nanoTime()

    // Convert the list to a byte array.
    val bytes = ints.encodeToByteArray()

    val endSerialize = System.nanoTime()

    // Convert the array to a list of ints again.
    val intsAgain = bytes.decodeToIntegerList()

    val endDeserialize = System.nanoTime()

    if (ints != intsAgain) {
        throw ArithmeticException("Serialization went wrong! Results should not be used!")
    }

    return Pair(endSerialize - startSerialize, endDeserialize - endSerialize)
}

/**
 * This script prints the median of 10000 repetitions of
 * (de)serializing integers using the EVA protocol.
 */
fun main() {
    val serializeResults = mutableListOf<Long>()
    val deserializeResults = mutableListOf<Long>()

    repeat (10000) {
        val (ser, deser) = measureSerialize()
        serializeResults.add(ser)
        deserializeResults.add(deser)
    }

    println("Serialization time: ${median(serializeResults)} Deserialization time: ${median(deserializeResults)}")
}