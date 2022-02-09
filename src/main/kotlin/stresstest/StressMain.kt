package stresstest

import mu.KLogger
import mu.KotlinLogging

private fun executeLine(inputString: String, app: StressApplication, logger: KLogger): Boolean {
    val input = inputString.toLowerCase().split(" ")

    when (input[0]) {
        "info" -> {
            logger.info{ "StressCommunity: ${app.printInfo()} peers" }
        }
        "test" -> {
            app.test()
        }
    }

    return true
}

fun main() {
    val logger = KotlinLogging.logger {}
    val app = StressApplication()

    var last = System.currentTimeMillis()

    while (true) {

        if (System.currentTimeMillis() > last + 5000) {
            if (app.printInfo() > 0) {
                logger.info{ "StressCommunity: ${app.printInfo()} peers" }
                last = Long.MAX_VALUE
            }
        }

        val input = readLine()!!

        if (!executeLine(input, app, logger)) {
            break
        }
    }
}