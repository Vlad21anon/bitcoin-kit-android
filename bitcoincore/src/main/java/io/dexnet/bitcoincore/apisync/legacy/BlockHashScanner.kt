package io.dexnet.bitcoincore.apisync.legacy

import io.dexnet.bitcoincore.core.IApiSyncerListener
import io.dexnet.bitcoincore.core.IApiTransactionProvider
import io.dexnet.bitcoincore.extensions.toReversedByteArray
import io.dexnet.bitcoincore.managers.IRestoreKeyConverter
import io.dexnet.bitcoincore.models.BlockHash
import io.dexnet.bitcoincore.models.PublicKey

class BlockHashScanner(
    private val restoreKeyConverter: IRestoreKeyConverter,
    private val transactionProvider: IApiTransactionProvider,
    private val helper: IBlockHashScanHelper
) {

    var listener: IApiSyncerListener? = null

    fun getBlockHashes(externalKeys: List<PublicKey>, internalKeys: List<PublicKey>): BlockHashesResponse {
        val externalAddresses = externalKeys.map {
            restoreKeyConverter.keysForApiRestore(it)
        }
        val internalAddresses = internalKeys.map {
            restoreKeyConverter.keysForApiRestore(it)
        }
        val allAddresses = externalAddresses.flatten() + internalAddresses.flatten()
        val transactions = transactionProvider.transactions(allAddresses, null)

        if (transactions.isEmpty()) {
            return BlockHashesResponse(listOf(), -1, -1)
        }

        listener?.onTransactionsFound(transactions.size)

        val addressItems = transactions.flatMap { it.addressItems }
        val externalLastUsedIndex = helper.lastUsedIndex(externalAddresses, addressItems)
        val internalLastUsedIndex = helper.lastUsedIndex(internalAddresses, addressItems)

        val blockHashes = transactions.map {
            BlockHash(it.blockHash.toReversedByteArray(), it.blockHeight, 0)
        }

        return BlockHashesResponse(blockHashes, externalLastUsedIndex, internalLastUsedIndex)
    }

    data class BlockHashesResponse(
        val blockHashes: List<BlockHash>,
        val externalLastUsedIndex: Int,
        val internalLastUsedIndex: Int
    )

}
