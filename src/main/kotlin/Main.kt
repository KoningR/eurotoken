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
            "balance" -> {
                logger.info("Your balance is ${app.getBalance()} Eurotoken.")
            }
            "generate" -> {
                if (input.size == 2) {
                    app.generate(input[1].toLong())
                } else {
                    app.generate()
                }

                logger.info("Generated Eurotoken.")
            }
            "send" -> {
                if (input.size == 3) {
                    app.send(input[1], input[2].toLong())
                } else {
                    app.send()
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