package old.token

import mu.KLogger
import mu.KotlinLogging
import java.io.File
import java.io.FileNotFoundException

private fun executeLine(inputString: String, app: Application, logger: KLogger): Boolean {
    val input = inputString.toLowerCase().split(" ")

    when (input[0]) {
        "info" -> {
            app.printInfo()
        }
        "get_balance" -> {
            logger.info("Your balance is ${app.getBalance()} Eurotoken.")
        }
        "create_coin" -> {
            if (input.size == 2) {
                app.createCoin(input[1].toLong())
            } else {
                app.createCoin()
            }

            logger.info("Generated Eurotoken. Your balance is now ${app.getBalance()} Eurotoken.")
        }
        "send_coin" -> {
            when (input.size) {
                2 -> {
                    app.sendCoin(input[1].toLong())
                }
                else -> {
                    app.sendCoin()
                }
            }

            logger.info("Your balance is now ${app.getBalance()} Eurotoken.")
        }
        "transaction_history" -> {
            if (input.size == 2) {
                app.transactionHistory(input[1])
            }
        }
        "stop" -> {
            app.stop()

            logger.info("Shutting down...")
            return false
        }
        else -> {
            logger.info("Unknown command")
        }
    }

    return true
}

fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    logger.info("Starting Eurotoken client...")

    val app = Application()

    if (args.isNotEmpty()) {
        try {
            val reader = File(args[0]).inputStream().bufferedReader()

            reader.forEachLine {
                executeLine(it, app, logger)
            }
        } catch (e: FileNotFoundException) {
            logger.info("Could not find script file ${args[0]}.")
        }
    }

    while (true) {
        val input = readLine()!!

        if (!executeLine(input, app, logger)) {
            break
        }
    }
}