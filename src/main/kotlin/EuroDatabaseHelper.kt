import nl.tudelft.eurotoken.sqldelight.EurotokenQueries
import nl.tudelft.eurotoken.sqldelight.GetCoinHistory
import java.lang.Exception

class EuroDatabaseHelper(private val query: EurotokenQueries) {
    internal fun getBalance(): Long {
        return query.getBalance().executeAsOne()
    }

    internal fun createCoin(amount: Long = 1, ownPublicKey: ByteArray): Array<ByteArray> {
        val coins = arrayListOf<ByteArray>()

        val transactionId = createTransaction(ownPublicKey)

        repeat(amount.toInt()) {
            query.insertEurotoken(EuroGenerator.generateToken())
            val euroTokenId = query.selectLastEuroToken().executeAsOne()
            query.insertTransactionEurotoken(transactionId, euroTokenId)

            coins.add(euroTokenId)
        }

        return coins.toTypedArray()
    }

    internal fun addOwnedCoin(eurotokenId: ByteArray, ownPublicKey: ByteArray) {
        val transactionId = createTransaction(ownPublicKey)

        query.insertEurotoken(eurotokenId)
        val euroTokenId = query.selectLastEuroToken().executeAsOne()
        query.insertTransactionEurotoken(transactionId, euroTokenId)
    }

    internal fun getAndMarkAsSent(recipient: ByteArray, amount: Long): List<ByteArray> {
        // TODO: Combine as 1 atomic SQL operation.

        val eurotokens = query.getOwnedEurotoken(amount).executeAsList()

        if (eurotokens.size != amount.toInt()) {
            throw Exception("The database did not return the correct amount of Eurotoken.")
        }

        val transactionId = createTransaction(recipient)

        for (eurotoken in eurotokens) {
            query.markEurotokenAsSent(eurotoken)
            query.insertTransactionEurotoken(transactionId, eurotoken)
        }

        return eurotokens
    }

    internal fun getCoinHistory(eurotokenId: ByteArray): List<GetCoinHistory> {
        return query.getCoinHistory(eurotokenId).executeAsList()
    }

    private fun createTransaction(recipient: ByteArray): Long {
        // TODO: Combine into 1 atomic transaction.

        query.insertTransaction(recipient)
        return query.selectLastTransaction().executeAsOne()
    }
}