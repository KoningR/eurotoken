import com.squareup.sqldelight.db.SqlDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver
import com.squareup.sqldelight.sqlite.driver.JdbcSqliteDriver.Companion.IN_MEMORY
import mu.KotlinLogging
import nl.tudelft.ipv8.*
import nl.tudelft.ipv8.attestation.trustchain.BlockListener
import nl.tudelft.ipv8.attestation.trustchain.TrustChainBlock
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainSQLiteStore
import nl.tudelft.ipv8.keyvault.JavaCryptoProvider
import nl.tudelft.ipv8.messaging.EndpointAggregator
import nl.tudelft.ipv8.messaging.udp.UdpEndpoint
import nl.tudelft.ipv8.peerdiscovery.DiscoveryCommunity
import nl.tudelft.ipv8.peerdiscovery.strategy.PeriodicSimilarity
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomChurn
import nl.tudelft.ipv8.peerdiscovery.strategy.RandomWalk
import nl.tudelft.ipv8.sqldelight.Database
import java.net.InetAddress

class Application {

    companion object {
        const val BLOCK_TYPE = "euro_block"
    }

    private val logger = KotlinLogging.logger {}

    private val ipv8: IPv8
    private val euroCommunity: EuroCommunity
    private val trustchainCommunity: TrustChainCommunity

    init {
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
        ipv8.start()

        euroCommunity = ipv8.getOverlay()!!
        trustchainCommunity = ipv8.getOverlay()!!

        trustchainCommunity.addListener(BLOCK_TYPE, object : BlockListener {
            override fun onBlockReceived(block: TrustChainBlock) {
                logger.info("onBlockReceived: ${block.blockId} ${block.transaction}")
                trustchainCommunity.createAgreementBlock(block, mapOf<Any?, Any?>())
            }
        })
    }

    fun stop() {
        ipv8.stop()
    }

    fun printInfo() {
        val peers = euroCommunity.getPeers()
        logger.info("EuroCommunity: ${peers.size} peers")

        for (peer in peers) {
            logger.info(peer.mid)
        }
    }

    fun send() {
        for (peer in euroCommunity.getPeers()) {
            val transaction = mapOf("message" to "1")
            val publicKey = peer.publicKey.keyToBin()
            trustchainCommunity.createProposalBlock(BLOCK_TYPE, transaction, publicKey)
        }
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
        Database.Schema.create(driver)
        val database = Database(driver)
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
