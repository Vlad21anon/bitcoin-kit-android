package io.dexnet.bitcoincore.apisync.model

data class BlockHeaderItem(
    val hash: ByteArray,
    val height: Int,
    val timestamp: Long
)
