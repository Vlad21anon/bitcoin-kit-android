package io.dexnet.tools

import io.dexnet.bitcoincash.MainNetBitcoinCash
import io.dexnet.bitcoincash.TestNetBitcoinCash
import io.dexnet.bitcoincore.extensions.toHexString
import io.dexnet.bitcoincore.io.BitcoinOutput
import io.dexnet.bitcoincore.models.Block
import io.dexnet.bitcoincore.network.Network
import io.dexnet.bitcoinkit.MainNet
import io.dexnet.bitcoinkit.TestNet
import io.dexnet.dashkit.MainNetDash
import io.dexnet.dashkit.TestNetDash
import io.dexnet.ecash.MainNetECash
import io.dexnet.litecoinkit.MainNetLitecoin
import io.dexnet.litecoinkit.TestNetLitecoin
import java.io.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

class BuildCheckpoints : CheckpointSyncer.Listener {

    private val syncers = mutableListOf<CheckpointSyncer>().also {
        // Bitcoin
        it.add(CheckpointSyncer(MainNet(), 2016, 1, this))
        it.add(CheckpointSyncer(TestNet(), 2016, 1, this))

        // Bitcoin Cash
        it.add(CheckpointSyncer(MainNetBitcoinCash(), 147, 147, this))
        it.add(CheckpointSyncer(TestNetBitcoinCash(), 147, 147, this))

        // Dash
        it.add(CheckpointSyncer(MainNetDash(), 24, 24, this))
        it.add(CheckpointSyncer(TestNetDash(), 24, 24, this))

        // Litecoin
        it.add(CheckpointSyncer(MainNetLitecoin(), 2016, 2, this))
        it.add(CheckpointSyncer(TestNetLitecoin(), 2016, 2, this))

        // Ecash
        it.add(CheckpointSyncer(MainNetECash(), 147, 147, this))
    }

    fun build(checkpoint: Block) {
        println(" ================== CHECKPOINT ================== ")
        println(serialize(checkpoint).toHexString())
        println(" ================================================ ")
    }

    fun sync() {
        syncers.forEach { it.start() }
    }

    override fun onSync(network: Network, checkpoints: List<Block>) {
        val networkName = network.javaClass.simpleName
        val checkpointFile = "${packagePath(network)}/src/main/resources/${networkName}.checkpoint"

        writeCheckpoints(checkpointFile, checkpoints)

        if (syncers.none { !it.isSynced }) {
            exitProcess(0)
        }
    }

    // Writing to file

    private fun writeCheckpoints(checkpointFile: String, checkpoints: List<Block>) {
        val file = File(checkpointFile)
        val fileOutputStream: OutputStream = FileOutputStream(file)
        val outputStream: Writer = OutputStreamWriter(fileOutputStream, StandardCharsets.US_ASCII)

        val buffer = ByteBuffer.allocate(80 + 4 + 32) // header + block height + block hash
        val writer = PrintWriter(outputStream)

        checkpoints.forEach {
            buffer.put(serialize(it))
            writer.println(buffer.array().toHexString())
            buffer.clear()
        }

        writer.close()
    }

    private fun serialize(block: Block): ByteArray {
        val payload = BitcoinOutput().also {
            it.writeInt(block.version)
            it.write(block.previousBlockHash)
            it.write(block.merkleRoot)
            it.writeUnsignedInt(block.timestamp)
            it.writeUnsignedInt(block.bits)
            it.writeUnsignedInt(block.nonce)
            it.writeInt(block.height)
            it.write(block.headerHash)
        }

        return payload.toByteArray()
    }

    private fun packagePath(network: Network): String {
        return when (network) {
            is MainNet,
            is TestNet -> "bitcoinkit"
            is MainNetBitcoinCash,
            is TestNetBitcoinCash -> "bitcoincashkit"
            is MainNetDash,
            is TestNetDash -> "dashkit"
            is MainNetLitecoin,
            is TestNetLitecoin -> "litecoinkit"
            is MainNetECash -> "ecashkit"
            else -> throw Exception("Invalid network: ${network.javaClass.name}")
        }
    }
}
