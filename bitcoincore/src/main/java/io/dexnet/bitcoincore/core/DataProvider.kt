package io.dexnet.bitcoincore.core

import io.dexnet.bitcoincore.blocks.IBlockchainDataListener
import io.dexnet.bitcoincore.extensions.hexToByteArray
import io.dexnet.bitcoincore.extensions.toReversedHex
import io.dexnet.bitcoincore.managers.UnspentOutputProvider
import io.dexnet.bitcoincore.models.BalanceInfo
import io.dexnet.bitcoincore.models.Block
import io.dexnet.bitcoincore.models.BlockInfo
import io.dexnet.bitcoincore.models.Transaction
import io.dexnet.bitcoincore.models.TransactionFilterType
import io.dexnet.bitcoincore.models.TransactionInfo
import io.dexnet.bitcoincore.storage.TransactionWithBlock
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit

class DataProvider(
        private val storage: IStorage,
        private val unspentOutputProvider: UnspentOutputProvider,
        private val transactionInfoConverter: ITransactionInfoConverter
) : IBlockchainDataListener {

    interface Listener {
        fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>)
        fun onTransactionsDelete(hashes: List<String>)
        fun onBalanceUpdate(balance: BalanceInfo)
        fun onLastBlockInfoUpdate(blockInfo: BlockInfo)
    }

    var listener: Listener? = null
    private val balanceUpdateSubject: PublishSubject<Boolean> = PublishSubject.create()
    private val balanceSubjectDisposable: Disposable

    //  Getters
    var balance: BalanceInfo = unspentOutputProvider.getBalance()
        private set(value) {
            if (value != field) {
                field = value
                listener?.onBalanceUpdate(field)
            }
        }

    var lastBlockInfo: BlockInfo?
        private set

    init {
        lastBlockInfo = storage.lastBlock()?.let {
            blockInfo(it)
        }

        balanceSubjectDisposable = balanceUpdateSubject.debounce(500, TimeUnit.MILLISECONDS)
                .subscribe {
                    balance = unspentOutputProvider.getBalance()
                }
    }

    override fun onBlockInsert(block: Block) {
        if (block.height > lastBlockInfo?.height ?: 0) {
            val blockInfo = blockInfo(block)

            lastBlockInfo = blockInfo
            listener?.onLastBlockInfoUpdate(blockInfo)
            balanceUpdateSubject.onNext(true)
        }
    }

    override fun onTransactionsUpdate(inserted: List<Transaction>, updated: List<Transaction>, block: Block?) {
        listener?.onTransactionsUpdate(
                storage.getFullTransactionInfo(inserted.map { TransactionWithBlock(it, block) }).map { transactionInfoConverter.transactionInfo(it) },
                storage.getFullTransactionInfo(updated.map { TransactionWithBlock(it, block) }).map { transactionInfoConverter.transactionInfo(it) }
        )

        balanceUpdateSubject.onNext(true)
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listener?.onTransactionsDelete(hashes)
        balanceUpdateSubject.onNext(true)
    }

    fun clear() {
        balanceSubjectDisposable.dispose()
    }

    fun transactions(fromUid: String?, type: TransactionFilterType? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return Single.create { emitter ->
            val fromTransaction = fromUid?.let { storage.getValidOrInvalidTransaction(it) }
            val transactions = storage.getFullTransactionInfo(fromTransaction, type, limit)
            emitter.onSuccess(transactions.map { transactionInfoConverter.transactionInfo(it) })
        }
    }

    fun getRawTransaction(transactionHash: String): String? {
        val hashByteArray = transactionHash.hexToByteArray().reversedArray()
        return storage.getFullTransactionInfo(hashByteArray)?.rawTransaction
                ?: storage.getInvalidTransaction(hashByteArray)?.rawTransaction
    }

    fun getTransaction(transactionHash: String): TransactionInfo? {
        return storage.getFullTransactionInfo(transactionHash.hexToByteArray().reversedArray())?.let {
            transactionInfoConverter.transactionInfo(it)
        }
    }

    fun getSpendableUtxo() = unspentOutputProvider.getSpendableUtxo()

    private fun blockInfo(block: Block) = BlockInfo(
            block.headerHash.toReversedHex(),
            block.height,
            block.timestamp)

}
