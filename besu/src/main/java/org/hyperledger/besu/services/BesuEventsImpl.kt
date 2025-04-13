/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.services

import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.units.bigints.UInt256
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.api.query.LogsQuery
import org.hyperledger.besu.ethereum.chain.*
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.eth.sync.BlockBroadcaster
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.RemovalReason
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.evm.log.LogTopic
import org.hyperledger.besu.plugin.data.AddedBlockContext
import org.hyperledger.besu.plugin.data.BlockHeader
import org.hyperledger.besu.plugin.data.PropagatedBlockContext
import org.hyperledger.besu.plugin.services.BesuEvents
import org.hyperledger.besu.plugin.services.BesuEvents.*
//import org.jetbrains.annotations.Unmodifiable
import java.util.function.Supplier
import java.util.stream.Collectors

/** A concrete implementation of BesuEvents used in Besu plugin framework.  */
class BesuEventsImpl
/**
 * Constructor for BesuEventsImpl
 *
 * @param blockchain An instance of Blockchain
 * @param blockBroadcaster An instance of BlockBroadcaster
 * @param transactionPool An instance of TransactionPool
 * @param syncState An instance of SyncState
 * @param badBlockManager A cache of bad blocks encountered on the network
 */(
    private val blockchain: Blockchain,
    private val blockBroadcaster: BlockBroadcaster,
    private val transactionPool: TransactionPool,
    private val syncState: SyncState,
    private val badBlockManager: BadBlockManager
) : BesuEvents {
    override fun addBlockPropagatedListener(listener: BlockPropagatedListener): Long {
        return blockBroadcaster.subscribePropagateNewBlocks { block: Block, totalDifficulty: Difficulty ->
            listener.onBlockPropagated(
                blockPropagatedContext(
                    { block.header },
                    { block.body },
                    { totalDifficulty })
            )
        }
    }

    override fun removeBlockPropagatedListener(listenerIdentifier: Long) {
        blockBroadcaster.unsubscribePropagateNewBlocks(listenerIdentifier)
    }

    override fun addBlockAddedListener(listener: BlockAddedListener): Long {
        return blockchain.observeBlockAdded { event: BlockAddedEvent ->
            listener.onBlockAdded(
                blockAddedContext(
                    { event.block.header },
                    { event.block.body },
                    { event.transactionReceipts })
            )
        }
    }

    override fun removeBlockAddedListener(listenerIdentifier: Long) {
        blockchain.removeObserver(listenerIdentifier)
    }

    override fun addBlockReorgListener(listener: BlockReorgListener): Long {
        return blockchain.observeChainReorg { blockWithReceipts: BlockWithReceipts, chain: Blockchain? ->
            listener.onBlockReorg(
                blockAddedContext(
                    { blockWithReceipts.header },
                    { blockWithReceipts.block.body },
                    { blockWithReceipts.receipts })
            )
        }
    }

    override fun removeBlockReorgListener(listenerIdentifier: Long) {
        blockchain.removeObserver(listenerIdentifier)
    }

    override fun addInitialSyncCompletionListener(listener: InitialSyncCompletionListener): Long {
        return syncState.subscribeCompletionReached(listener)
    }

    override fun addTransactionAddedListener(listener: TransactionAddedListener): Long {
        return transactionPool.subscribePendingTransactions { transaction: Transaction? ->
            listener.onTransactionAdded(
                transaction
            )
        }
    }

    override fun removeTransactionAddedListener(listenerIdentifier: Long) {
        transactionPool.unsubscribePendingTransactions(listenerIdentifier)
    }

    override fun addTransactionDroppedListener(
        transactionDroppedListener: TransactionDroppedListener
    ): Long {
        return transactionPool.subscribeDroppedTransactions { transaction: Transaction?, reason: RemovalReason? ->
            transactionDroppedListener.onTransactionDropped(
                transaction
            )
        }
    }

    override fun removeTransactionDroppedListener(listenerIdentifier: Long) {
        transactionPool.unsubscribeDroppedTransactions(listenerIdentifier)
    }

    override fun addSyncStatusListener(syncStatusListener: SyncStatusListener): Long {
        return syncState.subscribeSyncStatus(syncStatusListener)
    }

    override fun removeSyncStatusListener(listenerIdentifier: Long) {
        syncState.unsubscribeSyncStatus(listenerIdentifier)
    }

    override fun addLogListener(
        addresses: List<Address>,
        topics: List<List<Bytes32>>,
        logListener: LogListener
    ): Long {
        val besuTopics: List<List<LogTopic>> =
            topics.stream()
                .map<MutableList<LogTopic>> { subList: List<Bytes32> ->
                    subList.stream().map<LogTopic> { bytes: Bytes32? -> LogTopic.wrap(bytes) }
                        .collect(Collectors.toUnmodifiableList<LogTopic>())
                }
                .collect(Collectors.toUnmodifiableList<List<LogTopic>>())

        val logsQuery = LogsQuery(addresses, besuTopics)

        return blockchain.observeLogs { logWithMetadata: LogWithMetadata? ->
            if (logsQuery.matches(LogWithMetadata.fromPlugin(logWithMetadata))) {
                logListener.onLogEmitted(logWithMetadata)
            }
        }
    }

    override fun removeLogListener(listenerIdentifier: Long) {
        blockchain.removeObserver(listenerIdentifier)
    }

    override fun addBadBlockListener(listener: BadBlockListener): Long {
        return badBlockManager.subscribeToBadBlocks(listener)
    }

    override fun removeBadBlockListener(listenerIdentifier: Long) {
        badBlockManager.unsubscribeFromBadBlocks(listenerIdentifier)
    }

    companion object {
        private fun blockPropagatedContext(
            blockHeaderSupplier: Supplier<BlockHeader>,
            blockBodySupplier: Supplier<BlockBody>,
            totalDifficultySupplier: Supplier<Difficulty>
        ): PropagatedBlockContext {
            return object : PropagatedBlockContext {
                override fun getBlockHeader(): BlockHeader {
                    return blockHeaderSupplier.get()
                }

                override fun getBlockBody(): BlockBody {
                    return blockBodySupplier.get()
                }

                override fun getTotalDifficulty(): UInt256 {
                    return totalDifficultySupplier.get().toUInt256()
                }
            }
        }

        private fun blockAddedContext(
            blockHeaderSupplier: Supplier<BlockHeader>,
            blockBodySupplier: Supplier<BlockBody>,
            transactionReceiptsSupplier: Supplier<List<TransactionReceipt>>
        ): AddedBlockContext {
            return object : AddedBlockContext {
                override fun getBlockHeader(): BlockHeader {
                    return blockHeaderSupplier.get()
                }

                override fun getBlockBody(): BlockBody {
                    return blockBodySupplier.get()
                }

                override fun getTransactionReceipts(): List<TransactionReceipt> {
                    return transactionReceiptsSupplier.get()
                }
            }
        }
    }
}
