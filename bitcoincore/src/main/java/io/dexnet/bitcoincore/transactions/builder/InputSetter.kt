package io.dexnet.bitcoincore.transactions.builder

import io.dexnet.bitcoincore.DustCalculator
import io.dexnet.bitcoincore.core.IPublicKeyManager
import io.dexnet.bitcoincore.core.ITransactionDataSorterFactory
import io.dexnet.bitcoincore.core.PluginManager
import io.dexnet.bitcoincore.managers.IUnspentOutputSelector
import io.dexnet.bitcoincore.models.TransactionDataSortType
import io.dexnet.bitcoincore.models.TransactionInput
import io.dexnet.bitcoincore.storage.InputToSign
import io.dexnet.bitcoincore.storage.UnspentOutput
import io.dexnet.bitcoincore.transactions.TransactionSizeCalculator
import io.dexnet.bitcoincore.transactions.scripts.ScriptType
import io.dexnet.bitcoincore.utils.IAddressConverter

class InputSetter(
    private val unspentOutputSelector: IUnspentOutputSelector,
    private val publicKeyManager: IPublicKeyManager,
    private val addressConverter: IAddressConverter,
    private val changeScriptType: ScriptType,
    private val transactionSizeCalculator: TransactionSizeCalculator,
    private val pluginManager: PluginManager,
    private val dustCalculator: DustCalculator,
    private val transactionDataSorterFactory: ITransactionDataSorterFactory
) {
    fun setInputs(mutableTransaction: MutableTransaction, feeRate: Int, senderPay: Boolean, sortType: TransactionDataSortType) {
        val value = mutableTransaction.recipientValue
        val dust = dustCalculator.dust(changeScriptType)
        val unspentOutputInfo = unspentOutputSelector.select(
            value,
            feeRate,
            mutableTransaction.recipientAddress.scriptType,
            changeScriptType,
            senderPay, dust,
            mutableTransaction.getPluginDataOutputSize()
        )

        val sorter = transactionDataSorterFactory.sorter(sortType)
        val unspentOutputs = sorter.sortUnspents(unspentOutputInfo.outputs)

        for (unspentOutput in unspentOutputs) {
            mutableTransaction.addInput(inputToSign(unspentOutput))
        }

        mutableTransaction.recipientValue = unspentOutputInfo.recipientValue

        // Add change output if needed
        unspentOutputInfo.changeValue?.let { changeValue ->
            val changePubKey = publicKeyManager.changePublicKey()
            val changeAddress = addressConverter.convert(changePubKey, changeScriptType)

            mutableTransaction.changeAddress = changeAddress
            mutableTransaction.changeValue = changeValue
        }

        pluginManager.processInputs(mutableTransaction)
    }

    fun setInputs(mutableTransaction: MutableTransaction, unspentOutput: UnspentOutput, feeRate: Int) {
        if (unspentOutput.output.scriptType != ScriptType.P2SH) {
            throw TransactionBuilder.BuilderException.NotSupportedScriptType()
        }

        setInputs(mutableTransaction, listOf(unspentOutput), feeRate)
    }

    fun setInputs(mutableTransaction: MutableTransaction, unspentOutputs: List<UnspentOutput>, feeRate: Int) {
        // Calculate fee
        val transactionSize =
            transactionSizeCalculator.transactionSize(unspentOutputs.map { it.output }, listOf(mutableTransaction.recipientAddress.scriptType), mutableTransaction.getPluginDataOutputSize())

        val fee = transactionSize * feeRate

        val value = unspentOutputs.sumOf { it.output.value }
        if (value < fee) {
            throw TransactionBuilder.BuilderException.FeeMoreThanValue()
        }

        // Add to mutable transaction
        unspentOutputs.forEach {unspentOutput ->
            mutableTransaction.addInput(inputToSign(unspentOutput))
        }
        mutableTransaction.recipientValue = value - fee
    }

    private fun inputToSign(unspentOutput: UnspentOutput): InputToSign {
        val previousOutput = unspentOutput.output
        val transactionInput = TransactionInput(previousOutput.transactionHash, previousOutput.index.toLong())

        return InputToSign(transactionInput, previousOutput, unspentOutput.publicKey)
    }
}
