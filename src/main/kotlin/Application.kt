import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.eurotoken.sqldelight.Database
import nl.tudelft.ipv8.IPv8
import nl.tudelft.ipv8.IPv8Configuration
import nl.tudelft.ipv8.OverlayConfiguration
import nl.tudelft.ipv8.Peer
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.eva.EVAProtocol
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.net.InetAddress

class Application {

    private val logger = KotlinLogging.logger {}
    private val dispatcher = Dispatchers.IO
    private val scope = CoroutineScope(dispatcher)

    private val myPublicKey: ByteArray

    private val ipv8: IPv8
    private val euroCommunity: EuroCommunity

    private val euroDatabase: EuroDatabaseHelper

    private var startTime: Long = -1L
    private val coinTimes: MutableList<Pair<Long, Long>> = mutableListOf()

    init {
        val driver: SqlDriver = JdbcSqliteDriver(IN_MEMORY)
        Database.Schema.create(driver)
        euroDatabase = EuroDatabaseHelper(Database(driver).eurotokenQueries)

        val myKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(myKey)
        val udpEndpoint = UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))
        val endpoint = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createEuroCommunity()
        ), walkerInterval = 1.0)

        myPublicKey = myPeer.publicKey.keyToBin()

        ipv8 = IPv8(endpoint, config, myPeer)
        ipv8.start(dispatcher)

        euroCommunity = ipv8.getOverlay()!!

        euroCommunity.evaProtocol = EVAProtocol(euroCommunity, scope)

        euroCommunity.setOnEVAReceiveCompleteCallback(fun (peer: Peer, info: String, id: String, data: ByteArray?) {
            val currentTime = System.currentTimeMillis()
            if (startTime < 0) {
                startTime = currentTime
            }

            if (data == null) {
                logger.info { "Data was null." }
                return
            }

            if (!EuroGenerator.verifyToken(data)) {
                logger.info { "Token could not be verified!" }
                return
            }

            euroDatabase.addOwnedCoin(data, myPublicKey)

            val balance = getBalance()
            coinTimes.add(Pair(balance, currentTime - startTime))
            if (balance == 100L) {
                logger.warn { coinTimes.joinToString(", ", "[", "]") }
            }

            logger.info{ "Received a Eurotoken!" }
            logger.info{ "Balance is now: ${euroDatabase.getBalance()}" }
        })

        logger.info("My public key: ${getPublicKeyHex(myPeer)}")
    }

    fun printInfo() {
        val peers = euroCommunity.getPeers()
        logger.info("EuroCommunity: ${peers.size} peers")

        for (peer in peers) {
            logger.info(getPublicKeyHex(peer))
        }
    }

    fun getBalance(): Long {
        return euroDatabase.getBalance()
    }

    fun createCoin(amount: Long = 1) {
        if (amount < 1) {
            logger.info("Please enter a valid amount.")
            return
        }

        val coins = euroDatabase.createCoin(amount, myPublicKey)
        coins.forEach {logger.info("Coin ID: ${it.toHex()}")}
    }

    fun sendCoin(amount: Long = 1) {
        for (peer in euroCommunity.getPeers()) {
            sendCoin(peer, amount)
        }
    }

    fun sendCoin(peer: Peer, amount: Long) {
        if (amount < 1) {
            logger.info("Please enter a valid amount, not ${amount}.")
            return
        }

        if (getBalance() < amount) {
            logger.info("You do not have enough Eurotoken.")
            return
        }

        val publicKey = JavaCryptoProvider.keyFromPublicBin(getPublicKeyHex(peer).hexToBytes()).keyToBin()

        // TODO: Add a mechanism to mark as sent only when the network has received a token.

        val eurotokens = euroDatabase.getAndMarkAsSent(publicKey, amount)

        scope.launch {
            for (token in eurotokens) {
                euroCommunity.evaSendBinary(peer, euroCommunity.serviceId, java.util.UUID.randomUUID().toString(), token)
            }

            logger.info("Sending Eurotoken over Trustchain...")
        }
    }

    fun transactionHistory(eurotokenIdString: String) {
        // TODO: Improve the protocol to verify history using signatures.

        val eurotokenId = eurotokenIdString.hexToBytes()
        val history = euroDatabase.getCoinHistory(eurotokenId)
        for (transaction in history) {
            logger.info("Transaction[Time: ${transaction.time},\n Recipient: ${transaction.recipient.toHex()},\n id: ${transaction.transaction_id}]")
        }
    }

    fun stop() {
        ipv8.stop()
    }

    private fun createEuroCommunity(): OverlayConfiguration<EuroCommunity> {
        val settings = TrustChainSettings()
        val driver: SqlDriver = JdbcSqliteDriver(IN_MEMORY)
        // Eurotoken also has a generated database class.
        nl.tudelft.ipv8.sqldelight.Database.Schema.create(driver)
        val database = nl.tudelft.ipv8.sqldelight.Database(driver)
        val store = TrustChainSQLiteStore(database)
        val randomWalk = RandomWalk.Factory(timeout = 3.0, peers = 20)
        return OverlayConfiguration(
            EuroCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    /**
     * Short helper method to return the hex string of a peer's entire public key.
     */
    private fun getPublicKeyHex(peer: Peer): String {
        return peer.publicKey.keyToBin().toHex()
    }
}
