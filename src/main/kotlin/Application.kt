import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import nl.tudelft.eurotoken.sqldelight.Database
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.attestation.trustchain.*
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore
import nl.tudelft.ipv8.attestation.trustchain.validation.TransactionValidator
import nl.tudelft.ipv8.attestation.trustchain.validation.ValidationResult
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.util.hexToBytes
import nl.tudelft.ipv8.util.toHex
import java.net.InetAddress

class Application {

    companion object {
        private const val BLOCK_TYPE = "euro_block"
        private const val TRANSACTION_TYPE = "euro_token"
    }

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
//            createDiscoveryCommunity(),
            createEuroCommunity()
        ), walkerInterval = 1.0)

        myPublicKey = myPeer.publicKey.keyToBin()

        ipv8 = IPv8(endpoint, config, myPeer)
        ipv8.start(dispatcher)

        euroCommunity = ipv8.getOverlay()!!

        euroCommunity.registerTransactionValidator(BLOCK_TYPE, object : TransactionValidator {
            override fun validate(
                block: TrustChainBlock,
                database: TrustChainStore
            ): ValidationResult {

                if (block.isAgreement) {
                    return ValidationResult.Valid
                }

                if (!block.transaction.containsKey(TRANSACTION_TYPE)) {
                    return ValidationResult.Invalid(listOf("Block does not contain a token."))
                }

                val bytes = try {
                    block.transaction[TRANSACTION_TYPE].toString().hexToBytes()
                } catch (e: Exception) {
                    return ValidationResult.Invalid(listOf("Token could not be parsed from hex to byte array."))
                }

                return if (EuroGenerator.verifyToken(bytes)) {
                    ValidationResult.Valid
                } else {
                    ValidationResult.Invalid(listOf("Signature could not be verified."))
                }

            }
        })

        euroCommunity.registerBlockSigner(BLOCK_TYPE, object : BlockSigner {
            override fun onSignatureRequest(block: TrustChainBlock) {

                val currentTime = System.currentTimeMillis()

                if (startTime < 0) {
                    startTime = currentTime
                }

                // TODO: Currently, every received token get its own transaction.
                //  Perhaps find a way to group multiple received tokens into
                //  single transactions?
                euroCommunity.createAgreementBlock(block, mapOf<Any?, Any?>())

                // Blocks are already validated, so we can directly add them to our database.
                euroDatabase.addOwnedCoin(block.transaction[TRANSACTION_TYPE].toString().hexToBytes(), myPublicKey)

                val balance = getBalance()
                coinTimes.add(Pair(balance, currentTime - startTime))

                if (balance == 100L) {
                    logger.warn { coinTimes.joinToString(", ", "[", "]") }
                }

                logger.info("Received a Eurotoken: " + block.transaction[TRANSACTION_TYPE].toString())
                logger.info("Balance is now: ${euroDatabase.getBalance()}")
            }
        })

//        euroCommunity.addListener(BLOCK_TYPE, object : BlockListener {
//            override fun onBlockReceived(block: TrustChainBlock) {
//                logger.info("Received a block: ${block.timestamp} ${block.blockId} ${block.transaction}")
//            }
//        })

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
            sendCoin(getPublicKeyHex(peer), amount)
        }
    }

    fun sendCoin(publicKeyString: String, amount: Long) {
        if (amount < 1) {
            logger.info("Please enter a valid amount, not ${amount}.")
            return
        }

        if (getBalance() < amount) {
            logger.info("You do not have enough Eurotoken.")
            return
        }

        val publicKey = JavaCryptoProvider.keyFromPublicBin(publicKeyString.hexToBytes()).keyToBin()

        // TODO: Add a mechanism to mark as sent only when the network has received a token.

        val eurotokens = euroDatabase.getAndMarkAsSent(publicKey, amount)

        scope.launch {
            for (token in eurotokens) {
                val hex = token.toHex()

                val transaction = mapOf(TRANSACTION_TYPE to hex)
                euroCommunity.createProposalBlock(BLOCK_TYPE, transaction, publicKey)

                delay(20)
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

    private fun createDiscoveryCommunity(): OverlayConfiguration<DiscoveryCommunity> {
        val randomWalk = RandomWalk.Factory(timeout = 3.0, peers = 20)
        val randomChurn = RandomChurn.Factory()
        val periodicSimilarity = PeriodicSimilarity.Factory()
        return OverlayConfiguration(
            DiscoveryCommunity.Factory(),
            listOf(randomWalk, randomChurn, periodicSimilarity)
        )
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
