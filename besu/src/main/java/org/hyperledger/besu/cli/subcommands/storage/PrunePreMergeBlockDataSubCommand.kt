/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.cli.subcommands.storage

import org.hyperledger.besu.cli.config.NetworkName
import org.hyperledger.besu.cli.util.VersionProvider
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.chain.BlockchainStorage
import org.hyperledger.besu.ethereum.core.BlockBody
import org.hyperledger.besu.ethereum.core.Transaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import picocli.CommandLine
import java.util.concurrent.Executors
import kotlin.math.min

/** The prune pre-merge block data sub command  */
@CommandLine.Command(
    name = "prune-pre-merge-blocks",
    description = ["Prunes all pre-merge blocks and associated transaction receipts, leaving only headers"],
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider::class
)
class PrunePreMergeBlockDataSubCommand
/** Default constructor  */
    : Runnable {
    @Suppress("unused")
    @CommandLine.ParentCommand
    private val storageSubCommand: StorageSubCommand? = null

    @Suppress("unused")
    @CommandLine.Spec
    private val spec: CommandLine.Model.CommandSpec? = null

    @CommandLine.Option(
        names = ["--threads"],
        description = ["Specifies the number of concurrent threads to use when pruning (default: \${DEFAULT-VALUE})"]
    )
    private val threads = DEFAULT_THREADS

    @CommandLine.Option(
        names = ["--prune-range-size"],
        description = ["Specifies the size of block ranges to be pruned (default: \${DEFAULT-VALUE})"]
    )
    private val pruneRangeSize = DEFAULT_PRUNE_RANGE_SIZE

    override fun run() {
        val network = storageSubCommand!!.besuCommand!!.network
        val dataPath = storageSubCommand.besuCommand!!.dataDir()
        if (!SUPPORTED_NETWORKS.contains(network)) {
            LOG.error(
                "Unable to prune pre-merge blocks and transaction receipts for network: {}", network
            )
            return
        }

        LOG.info(
            "Pruning pre-merge blocks and transaction receipts, network={}, data path={}",
            network,
            dataPath
        )
        val mergeBlockNumber = getMergeBlockNumber(network)

        storageSubCommand.besuCommand!!.buildController().use { besuController ->
            val blockchainStorage =
                besuController.storageProvider.createBlockchainStorage(
                    besuController.protocolSchedule,
                    besuController.storageProvider.createVariablesStorage(),
                    besuController.dataStorageConfiguration
                )
            Executors.newFixedThreadPool(threads).use { executor ->
                var i: Long = 0
                while (i < mergeBlockNumber) {
                    val headerNumber = i
                    executor.submit { deleteBlockRange(headerNumber, mergeBlockNumber, blockchainStorage) }
                    i += pruneRangeSize.toLong()
                }
            }
        }
    }

    private fun deleteBlockRange(
        startBlockNumber: Long,
        mergeBlockNumber: Long,
        blockchainStorage: BlockchainStorage
    ) {
        val updater = blockchainStorage.updater()
        var headerNumber = startBlockNumber
        val endBlockNumber = min((startBlockNumber + pruneRangeSize).toDouble(), mergeBlockNumber.toDouble()).toLong()
        do {
            blockchainStorage
                .getBlockHash(headerNumber)
                .filter { h: Hash? -> blockchainStorage.getBlockBody(h).isPresent }
                .ifPresent { h: Hash? ->
                    updater.removeTransactionReceipts(h)
                    updater.removeTotalDifficulty(h)
                    blockchainStorage
                        .getBlockBody(h)
                        .map { bb: BlockBody -> bb.transactions }
                        .ifPresent { transactions: List<Transaction> ->
                            transactions.stream()
                                .map { obj: Transaction -> obj.hash }
                                .forEach { th: Hash? ->
                                    updater.removeTransactionLocation(
                                        th
                                    )
                                }
                        }
                    updater.removeBlockBody(h)
                }
        } while (++headerNumber < endBlockNumber)
        updater.commit()
        LOG.info("Completed deletion of block range {} to {}", startBlockNumber, endBlockNumber)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PrunePreMergeBlockDataSubCommand::class.java)

        private val SUPPORTED_NETWORKS: List<NetworkName> = java.util.List.of(NetworkName.MAINNET, NetworkName.SEPOLIA)
        private const val MAINNET_MERGE_BLOCK_NUMBER: Long = 15537393
        private const val SEPOLIA_MERGE_BLOCK_NUMBER: Long = 1735371

        private const val DEFAULT_THREADS = 4
        private const val DEFAULT_PRUNE_RANGE_SIZE = 10000

        private fun getMergeBlockNumber(network: NetworkName): Long {
            return when (network) {
                NetworkName.MAINNET -> MAINNET_MERGE_BLOCK_NUMBER
                NetworkName.SEPOLIA -> SEPOLIA_MERGE_BLOCK_NUMBER
                else -> throw RuntimeException("Unexpected network: $network")
            }
        }
    }
}
