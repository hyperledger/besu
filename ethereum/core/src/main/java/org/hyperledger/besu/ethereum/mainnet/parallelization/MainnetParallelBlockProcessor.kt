/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.parallelization

import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.BlockProcessingResult
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.MutableWorldState
import org.hyperledger.besu.ethereum.core.Transaction
import org.hyperledger.besu.ethereum.core.Withdrawal
import org.hyperledger.besu.ethereum.mainnet.*
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecBuilder.BlockProcessorBuilder
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult
import org.hyperledger.besu.evm.blockhash.BlockHashLookup
import org.hyperledger.besu.evm.worldstate.WorldUpdater
import org.hyperledger.besu.metrics.BesuMetricCategory
import org.hyperledger.besu.plugin.services.MetricsSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class MainnetParallelBlockProcessor(
    transactionProcessor: MainnetTransactionProcessor,
    transactionReceiptFactory: TransactionReceiptFactory,
    blockReward: Wei,
    miningBeneficiaryCalculator: MiningBeneficiaryCalculator,
    skipZeroBlockRewards: Boolean,
    protocolSchedule: ProtocolSchedule,
    metricsSystem: MetricsSystem
) : MainnetBlockProcessor(
    transactionProcessor,
    transactionReceiptFactory,
    blockReward,
    miningBeneficiaryCalculator,
    skipZeroBlockRewards,
    protocolSchedule
) {
    private val confirmedParallelizedTransactionCounter =
        Optional.of(
            metricsSystem.createCounter(
                BesuMetricCategory.BLOCK_PROCESSING,
                "parallelized_transactions_counter",
                "Counter for the number of parallelized transactions during block processing"
            )
        )

    private val conflictingButCachedTransactionCounter =
        Optional.of(
            metricsSystem.createCounter(
                BesuMetricCategory.BLOCK_PROCESSING,
                "conflicted_transactions_counter",
                "Counter for the number of conflicted transactions during block processing"
            )
        )

    override fun getTransactionProcessingResult(
        preProcessingContext: Optional<PreprocessingContext>?,
        worldState: MutableWorldState?,
        blockUpdater: WorldUpdater?,
        privateMetadataUpdater: PrivateMetadataUpdater?,
        blockHeader: BlockHeader?,
        blobGasPrice: Wei?,
        miningBeneficiary: Address?,
        transaction: Transaction?,
        location: Int,
        blockHashLookup: BlockHashLookup?
    ): TransactionProcessingResult {
        var transactionProcessingResult: TransactionProcessingResult? = null

        if (preProcessingContext!!.isPresent) {
            val parallelizedPreProcessingContext =
                preProcessingContext.get() as ParallelizedPreProcessingContext
            transactionProcessingResult =
                parallelizedPreProcessingContext
                    .parallelizedConcurrentTransactionProcessor
                    .applyParallelizedTransactionResult(
                        worldState,
                        miningBeneficiary,
                        transaction,
                        location,
                        confirmedParallelizedTransactionCounter,
                        conflictingButCachedTransactionCounter
                    )
                    .orElse(null)
        }

        return transactionProcessingResult
            ?: super.getTransactionProcessingResult(
                preProcessingContext,
                worldState,
                blockUpdater,
                privateMetadataUpdater,
                blockHeader,
                blobGasPrice,
                miningBeneficiary,
                transaction,
                location,
                blockHashLookup
            )
    }

    override fun processBlock(
        protocolContext: ProtocolContext,
        blockchain: Blockchain,
        worldState: MutableWorldState,
        blockHeader: BlockHeader,
        transactions: List<Transaction>,
        ommers: List<BlockHeader>,
        maybeWithdrawals: Optional<List<Withdrawal>>,
        privateMetadataUpdater: PrivateMetadataUpdater
    ): BlockProcessingResult {
        val blockProcessingResult =
            super.processBlock(
                protocolContext,
                blockchain,
                worldState,
                blockHeader,
                transactions,
                ommers,
                maybeWithdrawals,
                privateMetadataUpdater,
                ParallelTransactionPreprocessing(transactionProcessor, executor)
            )

        if (blockProcessingResult.isFailed) {
            // Fallback to non-parallel processing if there is a block processing exception .
            LOG.info(
                "Parallel transaction processing failure. Falling back to non-parallel processing for block #{} ({})",
                blockHeader.number,
                blockHeader.blockHash
            )
            return super.processBlock(
                protocolContext,
                blockchain,
                worldState,
                blockHeader,
                transactions,
                ommers,
                maybeWithdrawals,
                privateMetadataUpdater,
                NoPreprocessing()
            )
        }
        return blockProcessingResult
    }

    @JvmRecord
    internal data class ParallelizedPreProcessingContext(val parallelizedConcurrentTransactionProcessor: ParallelizedConcurrentTransactionProcessor) :
        PreprocessingContext

    class ParallelBlockProcessorBuilder
        (val metricsSystem: MetricsSystem) : BlockProcessorBuilder {
        override fun apply(
            transactionProcessor: MainnetTransactionProcessor,
            transactionReceiptFactory: TransactionReceiptFactory,
            blockReward: Wei,
            miningBeneficiaryCalculator: MiningBeneficiaryCalculator,
            skipZeroBlockRewards: Boolean,
            protocolSchedule: ProtocolSchedule
        ): BlockProcessor {
            return MainnetParallelBlockProcessor(
                transactionProcessor,
                transactionReceiptFactory,
                blockReward,
                miningBeneficiaryCalculator,
                skipZeroBlockRewards,
                protocolSchedule,
                metricsSystem
            )
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(MainnetParallelBlockProcessor::class.java)

        private val NCPU = Runtime.getRuntime().availableProcessors()
        private val executor: Executor = Executors.newFixedThreadPool(NCPU)
    }
}
