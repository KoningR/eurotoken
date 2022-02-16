package evatest

import mu.KLogger
import mu.KotlinLogging

private fun executeLine(inputString: String, app: EvaApplication, logger: KLogger): Boolean {
    val input = inputString.toLowerCase().split(" ")

    when (input[0]) {
        "info" -> {
            logger.info{ "EvaCommunity: ${app.printInfo()} peers" }
        }
        "test" -> {
            app.test()
        }
    }

    return true
}

fun main() {
    val logger = KotlinLogging.logger {}
    val app = EvaApplication()

    while (true) {
        val input = readLine()!!

        if (!executeLine(input, app, logger)) {
            break
        }
    }
}