package io.dexnet.bitcoincore.transactions

import io.dexnet.bitcoincore.core.IPublicKeyManager
import io.dexnet.bitcoincore.core.IStorage
import io.dexnet.bitcoincore.managers.BloomFilterManager
import io.dexnet.bitcoincore.storage.FullTransaction

class TransactionSyncer(
    private val storage: IStorage,
    private val transactionProcessor: PendingTransactionProcessor,
    private val invalidator: TransactionInvalidator,
    private val publicKeyManager: IPublicKeyManager
) {

    fun getNewTransactions(): List<FullTransaction> {
        return storage.getNewTransactions()
    }

    fun handleRelayed(transactions: List<FullTransaction>) {
        if (transactions.isEmpty()) return

        var needToUpdateBloomFilter = false

        try {
            transactionProcessor.processReceived(transactions, false)
        } catch (e: BloomFilterManager.BloomFilterExpired) {
            needToUpdateBloomFilter = true
        }

        if (needToUpdateBloomFilter) {
            publicKeyManager.fillGap()
        }
    }

    fun shouldRequestTransaction(hash: ByteArray): Boolean {
        return !storage.isRelayedTransactionExists(hash)
    }

    fun handleInvalid(fullTransaction: FullTransaction) {
        invalidator.invalidate(fullTransaction.header)
    }
}
