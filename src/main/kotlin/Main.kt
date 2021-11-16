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
                if (input.size == 2) {
                    app.send(input[1])
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