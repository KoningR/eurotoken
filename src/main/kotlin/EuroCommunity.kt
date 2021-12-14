import nl.tudelft.ipv8.Overlay
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCommunity
import nl.tudelft.ipv8.attestation.trustchain.TrustChainCrawler
import nl.tudelft.ipv8.attestation.trustchain.TrustChainSettings
import nl.tudelft.ipv8.attestation.trustchain.store.TrustChainStore

class EuroCommunity(settings: TrustChainSettings,
                    database: TrustChainStore,
                    crawler: TrustChainCrawler = TrustChainCrawler()
) : TrustChainCommunity(settings, database, crawler) {
    override val serviceId = "081a9685c1912a141279f8248xw3db5899c5dzb8"

    class Factory(
        private val settings: TrustChainSettings,
        private val database: TrustChainStore,
        private val crawler: TrustChainCrawler = TrustChainCrawler()
    ) : Overlay.Factory<EuroCommunity>(EuroCommunity::class.java) {
        override fun create(): EuroCommunity {
            return EuroCommunity(settings, database, crawler)
        }
    }
}
