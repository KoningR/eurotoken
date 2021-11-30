import mu.KotlinLogging

fun main() {
    val logger = KotlinLogging.logger {}
    logger.info("Starting Eurotoken client...")

    val app = Application()

    var running = true
    while (running) {

        val input = readLine()!!.toLowerCase().split(" ")

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
                if (input.size == 3) {
                    app.sendCoin(input[1], input[2].toLong())
                } else {
                    app.sendCoin()
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
                running = false

                logger.info("Shutting down...")
            }
            else -> {
                logger.info("Unknown command")
            }
        }

    }
}