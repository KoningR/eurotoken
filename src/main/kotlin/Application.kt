import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import nl.tudelft.eurotoken.sqldelight.Database
import nl.tudelft.eurotoken.sqldelight.EurotokenQueries
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

    private val ipv8: IPv8
    private val euroCommunity: EuroCommunity
    private val trustchainCommunity: TrustChainCommunity

    private val query: EurotokenQueries

    init {
        val driver: SqlDriver = JdbcSqliteDriver(IN_MEMORY)
        Database.Schema.create(driver)
        query = Database(driver).eurotokenQueries

        val myKey = JavaCryptoProvider.generateKey()
        val myPeer = Peer(myKey)
        val udpEndpoint = UdpEndpoint(8090, InetAddress.getByName("0.0.0.0"))
        val endpoint = EndpointAggregator(udpEndpoint, null)
        val config = IPv8Configuration(overlays = listOf(
            createDiscoveryCommunity(),
            createTrustChainCommunity(),
            createEuroCommunity()
        ), walkerInterval = 1.0)

        ipv8 = IPv8(endpoint, config, myPeer)
        ipv8.start(dispatcher)

        euroCommunity = ipv8.getOverlay()!!
        trustchainCommunity = ipv8.getOverlay()!!

        trustchainCommunity.registerTransactionValidator(BLOCK_TYPE, object : TransactionValidator {
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

        trustchainCommunity.registerBlockSigner(BLOCK_TYPE, object : BlockSigner {
            override fun onSignatureRequest(block: TrustChainBlock) {

                // Blocks are already validated, so we can directly add them to our database.
                query.insert(block.transaction[TRANSACTION_TYPE].toString().hexToBytes())

                trustchainCommunity.createAgreementBlock(block, mapOf<Any?, Any?>())
                logger.info("Received a Eurotoken.")
            }
        })

//        trustchainCommunity.addListener(BLOCK_TYPE, object : BlockListener {
//            override fun onBlockReceived(block: TrustChainBlock) {
//                logger.info("Received a block: ${block.timestamp} ${block.blockId} ${block.transaction}")
//            }
//        })

        logger.info("My public key: ${getPublicKeyHex(myPeer)}")
    }

    fun stop() {
        ipv8.stop()
    }

    fun printInfo() {
        val peers = euroCommunity.getPeers()
        logger.info("EuroCommunity: ${peers.size} peers")

        for (peer in peers) {
            logger.info(getPublicKeyHex(peer))
        }
    }

    fun getBalance(): Long {
        return query.getBalance().executeAsOne()
    }

    fun generate(amount: Long = 1) {
        if (amount < 1) {
            logger.info("Please enter a valid amount.")
            return
        }

        repeat(amount.toInt()) {
            query.insert(EuroGenerator.generateToken())
        }
    }

    fun send() {
        for (peer in euroCommunity.getPeers()) {
            val publicKey = peer.publicKey.keyToBin()
            sendEuroOverTrustchain(publicKey, 1)
        }
    }

    fun send(publicKeyString: String, amount: Long) {
        val publicKey = JavaCryptoProvider.keyFromPublicBin(publicKeyString.hexToBytes()).keyToBin()
        sendEuroOverTrustchain(publicKey, amount)
    }

    private fun getAndDeleteEurotoken(amount: Long): List<ByteArray>? {
       if (amount < 1) {
           logger.info("Please enter a valid amount, not ${amount}.")
           return null
       }

       val eurotokens = query.getEurotoken(amount).executeAsList()

       if (eurotokens.size < amount) {
           logger.info("You have ${eurotokens.size} Eurotoken, not ${amount}.")
           return null
       }

       query.deleteEurotoken(amount)

       return eurotokens
    }

    private fun sendEuroOverTrustchain(publicKey: ByteArray, amount: Long) {

        if (amount < 1) {
            logger.info("Please enter a valid amount, not ${amount}.")
            return
        }

        if (getBalance() < amount) {
            logger.info("You do not have enough Eurotoken.")
            return
        }

        val eurotokens = getAndDeleteEurotoken(amount)!!

        for (token in eurotokens) {
            val hex = token.toHex()

            val transaction = mapOf(TRANSACTION_TYPE to hex)
            trustchainCommunity.createProposalBlock(BLOCK_TYPE, transaction, publicKey)
        }

        logger.info("Sending Eurotoken over Trustchain...")
    }

    /**
     * Short helper method to return the hex string of a peer's entire public key.
     */
    private fun getPublicKeyHex(peer: Peer): String {
        return peer.publicKey.keyToBin().toHex()
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

    private fun createTrustChainCommunity(): OverlayConfiguration<TrustChainCommunity> {
        val settings = TrustChainSettings()
        val driver: SqlDriver = JdbcSqliteDriver(IN_MEMORY)
        // Eurotoken also has a generated database class.
        nl.tudelft.ipv8.sqldelight.Database.Schema.create(driver)
        val database = nl.tudelft.ipv8.sqldelight.Database(driver)
        val store = TrustChainSQLiteStore(database)
        val randomWalk = RandomWalk.Factory(timeout = 3.0, peers = 20)
        return OverlayConfiguration(
            TrustChainCommunity.Factory(settings, store),
            listOf(randomWalk)
        )
    }

    private fun createEuroCommunity(): OverlayConfiguration<EuroCommunity> {
        val randomWalk = RandomWalk.Factory(timeout = 3.0, peers = 20)
        return OverlayConfiguration(
            Overlay.Factory(EuroCommunity::class.java),
            listOf(randomWalk)
        )
    }
}
