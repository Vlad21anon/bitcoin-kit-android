package io.dexnet.bitcoincore.managers

import io.dexnet.bitcoincore.core.IStorage
import io.dexnet.bitcoincore.core.PluginManager
import io.dexnet.bitcoincore.models.BalanceInfo
import io.dexnet.bitcoincore.storage.UnspentOutput

class UnspentOutputProvider(private val storage: IStorage, private val confirmationsThreshold: Int = 6, val pluginManager: PluginManager) : IUnspentOutputProvider {
    override fun getSpendableUtxo(): List<UnspentOutput> {
        return getConfirmedUtxo().filter {
            pluginManager.isSpendable(it)
        }
    }

    fun getBalance(): BalanceInfo {
        val spendable = getSpendableUtxo().map { it.output.value }.sum()
        val unspendable = getUnspendableUtxo().map { it.output.value }.sum()

        return BalanceInfo(spendable, unspendable)
    }

    private fun getConfirmedUtxo(): List<UnspentOutput> {
        val unspentOutputs = storage.getUnspentOutputs()

        if (confirmationsThreshold == 0) return unspentOutputs

        val lastBlockHeight = storage.lastBlock()?.height ?: 0

        return unspentOutputs.filter {
            if (it.transaction.isOutgoing) {
                return@filter true
            }

            val block = it.block ?: return@filter false
            if (block.height <= lastBlockHeight - confirmationsThreshold + 1) {
                return@filter true
            }

            false
        }
    }

    private fun getUnspendableUtxo(): List<UnspentOutput> {
        return getConfirmedUtxo().filter {
            !pluginManager.isSpendable(it)
        }
    }
}
