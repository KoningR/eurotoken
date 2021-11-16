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
            "send" -> {
                app.send()

                logger.info("Sent!")
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