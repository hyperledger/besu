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
package org.hyperledger.besu.ethereum.mainnet

import com.google.common.annotations.VisibleForTesting
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.datatypes.TransactionType
import org.hyperledger.besu.datatypes.Wei
import org.hyperledger.besu.ethereum.BlockProcessingOutputs
import org.hyperledger.besu.ethereum.BlockProcessingResult
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing
import org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator
import org.hyperledger.besu.ethereum.mainnet.requests.RequestProcessingContext
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult
import org.hyperledger.besu.ethereum.trie.MerkleTrieException
import org.hyperledger.besu.ethereum.trie.common.StateRootMismatchException
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldStateUpdateAccumulator
import org.hyperledger.besu.evm.blockhash.BlockHashLookup
import org.hyperledger.besu.evm.tracing.OperationTracer
import org.hyperledger.besu.evm.worldstate.WorldState
import org.hyperledger.besu.evm.worldstate.WorldUpdater
import org.hyperledger.besu.plugin.ServiceManager
import org.hyperledger.besu.plugin.services.BesuService
import org.hyperledger.besu.plugin.services.BlockImportTracerProvider
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.text.MessageFormat
import java.util.*

abstract class AbstractBlockProcessor protected constructor(
    protected val transactionProcessor: MainnetTransactionProcessor,
    protected val transactionReceiptFactory: TransactionReceiptFactory,
    val blockReward: Wei,
    protected val miningBeneficiaryCalculator: MiningBeneficiaryCalculator,
    protected val skipZeroBlockRewards: Boolean,
    private val protocolSchedule: ProtocolSchedule
) : BlockProcessor {
    fun interface TransactionReceiptFactory {
        fun create(
            transactionType: TransactionType?,
            result: TransactionProcessingResult?,
            worldState: WorldState?,
            gasUsed: Long
        ): TransactionReceipt
    }

    private var blockImportTracerProvider: BlockImportTracerProvider? = null

    private fun getBlockImportTracer(
        protocolContext: ProtocolContext, header: BlockHeader
    ): BlockAwareOperationTracer? {
        if (blockImportTracerProvider == null) {
            // fetch from context once, and keep.
            blockImportTracerProvider =
                Optional.ofNullable(protocolContext.pluginServiceManager)
                    .flatMap { serviceManager: ServiceManager ->
                        serviceManager.getService(
                            BlockImportTracerProvider::class.java
                        )
                    } // if block import tracer provider is not specified by plugin, default to no tracing
//                    .orElse(BesuService?)
                    .orElse(object : BlockImportTracerProvider {
                        override fun getBlockImportTracer(blockHeader: org.hyperledger.besu.plugin.data.BlockHeader?): BlockAwareOperationTracer? {
                            return null
                        }
                    }) as BlockImportTracerProvider
        }

        return blockImportTracerProvider!!.getBlockImportTracer(header)
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
        return processBlock(
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

    @VisibleForTesting
    fun processBlock(
        protocolContext: ProtocolContext,
        blockchain: Blockchain,
        worldState: MutableWorldState,
        blockHeader: BlockHeader,
        transactions: List<Transaction>,
        ommers: List<BlockHeader>?,
        maybeWithdrawals: Optional<List<Withdrawal>>,
        privateMetadataUpdater: PrivateMetadataUpdater?,
        preprocessingBlockFunction: PreprocessingFunction
    ): BlockProcessingResult {
        val receipts: MutableList<TransactionReceipt> = ArrayList()
        var currentGasUsed: Long = 0
        var currentBlobGasUsed: Long = 0

        val protocolSpec = protocolSchedule.getByBlockHeader(blockHeader)
        val blockHashLookup =
            protocolSpec.blockHashProcessor.createBlockHashLookup(blockchain, blockHeader)
        val blockProcessingContext =
            BlockProcessingContext(
                blockHeader,
                worldState,
                protocolSpec,
                blockHashLookup,
                getBlockImportTracer(protocolContext, blockHeader)
            )
        protocolSpec.blockHashProcessor.process(blockProcessingContext)

        val miningBeneficiary = miningBeneficiaryCalculator.calculateBeneficiary(blockHeader)

        val maybeParentHeader =
            blockchain.getBlockHeader(blockHeader.parentHash)

        val blobGasPrice =
            maybeParentHeader
                .map { parentHeader: BlockHeader? ->
                    protocolSpec
                        .feeMarket
                        .blobGasPricePerGas(
                            ExcessBlobGasCalculator.calculateExcessBlobGasForParent(protocolSpec, parentHeader)
                        )
                }
                .orElse(Wei.ZERO)

        val preProcessingContext =
            preprocessingBlockFunction.run(
                protocolContext,
                privateMetadataUpdater,
                blockHeader,
                transactions,
                miningBeneficiary,
                blockHashLookup,
                blobGasPrice
            )

        var parallelizedTxFound = false
        var nbParallelTx = 0
        for (i in transactions.indices) {
            val transaction = transactions[i]
            if (!hasAvailableBlockBudget(blockHeader, transaction, currentGasUsed)) {
                return BlockProcessingResult(Optional.empty(), "provided gas insufficient")
            }
            val blockUpdater = worldState.updater()

            val transactionProcessingResult =
                getTransactionProcessingResult(
                    preProcessingContext,
                    worldState,
                    blockUpdater,
                    privateMetadataUpdater,
                    blockHeader,
                    blobGasPrice,
                    miningBeneficiary,
                    transaction,
                    i,
                    blockHashLookup
                )
            if (transactionProcessingResult.isInvalid) {
                val errorMessage =
                    MessageFormat.format(
                        "Block processing error: transaction invalid {0}. Block {1} Transaction {2}",
                        transactionProcessingResult.validationResult.errorMessage,
                        blockHeader.hash.toHexString(),
                        transaction.hash.toHexString()
                    )
                LOG.info(errorMessage)
                if (worldState is BonsaiWorldState) {
                    (blockUpdater as BonsaiWorldStateUpdateAccumulator).reset()
                }
                return BlockProcessingResult(Optional.empty(), errorMessage)
            }

            blockUpdater.commit()
            blockUpdater.markTransactionBoundary()

            currentGasUsed += transaction.gasLimit - transactionProcessingResult.gasRemaining
            if (transaction.versionedHashes.isPresent) {
                currentBlobGasUsed +=
                    (transaction.versionedHashes.get().size
                            * protocolSpec.gasCalculator.blobGasPerBlob)
            }

            val transactionReceipt =
                transactionReceiptFactory.create(
                    transaction.type, transactionProcessingResult, worldState, currentGasUsed
                )
            receipts.add(transactionReceipt)
            if (!parallelizedTxFound
                && transactionProcessingResult.isProcessedInParallel.isPresent
            ) {
                parallelizedTxFound = true
                nbParallelTx = 1
            } else if (transactionProcessingResult.isProcessedInParallel.isPresent) {
                nbParallelTx++
            }
        }
        if (blockHeader.blobGasUsed.isPresent
            && currentBlobGasUsed != blockHeader.blobGasUsed.get()
        ) {
            val errorMessage = String.format(
                "block did not consume expected blob gas: header %d, transactions %d",
                blockHeader.blobGasUsed.get(), currentBlobGasUsed
            )
            LOG.error(errorMessage)
            return BlockProcessingResult(Optional.empty(), errorMessage)
        }
        val maybeWithdrawalsProcessor =
            protocolSpec.withdrawalsProcessor
        if (maybeWithdrawalsProcessor.isPresent && maybeWithdrawals.isPresent) {
            try {
                maybeWithdrawalsProcessor
                    .get()
                    .processWithdrawals(maybeWithdrawals.get(), worldState.updater())
            } catch (e: Exception) {
                LOG.error("failed processing withdrawals", e)
                return BlockProcessingResult(Optional.empty(), e)
            }
        }

        var maybeRequests: Optional<List<Request?>> = Optional.empty()
        try {
            // EIP-7685: process EL requests
            val requestProcessor =
                protocolSpec.requestProcessorCoordinator
            if (requestProcessor.isPresent) {
                val requestProcessingContext =
                    RequestProcessingContext(blockProcessingContext, receipts)
                maybeRequests = Optional.of(requestProcessor.get().process(requestProcessingContext))
            }
        } catch (e: Exception) {
            LOG.error("failed processing requests", e)
            return BlockProcessingResult(Optional.empty(), e)
        }

        if (maybeRequests.isPresent && blockHeader.requestsHash.isPresent) {
            val calculatedRequestHash = BodyValidation.requestsHash(maybeRequests.get())
            val headerRequestsHash = blockHeader.requestsHash.get()
            if (calculatedRequestHash != headerRequestsHash) {
                return BlockProcessingResult(
                    Optional.empty(),
                    ("Requests hash mismatch, calculated: "
                            + calculatedRequestHash.toHexString()
                            + " header: "
                            + headerRequestsHash.toHexString())
                )
            }
        }

        if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
            // no need to log, rewardCoinbase logs the error.
            if (worldState is BonsaiWorldState) {
                (worldState.updater() as BonsaiWorldStateUpdateAccumulator).reset()
            }
            return BlockProcessingResult(Optional.empty(), "ommer too old")
        }

        try {
            worldState.persist(blockHeader)
        } catch (e: MerkleTrieException) {
            LOG.trace("Merkle trie exception during Transaction processing ", e)
            if (worldState is BonsaiWorldState) {
                (worldState.updater() as BonsaiWorldStateUpdateAccumulator).reset()
            }
            throw e
        } catch (ex: StateRootMismatchException) {
            LOG.error(
                "failed persisting block due to stateroot mismatch; expected {}, actual {}",
                ex.expectedRoot.toHexString(),
                ex.actualRoot.toHexString()
            )
            return BlockProcessingResult(Optional.empty(), ex.message)
        } catch (e: Exception) {
            LOG.error("failed persisting block", e)
            return BlockProcessingResult(Optional.empty(), e)
        }

        return BlockProcessingResult(
            Optional.of(BlockProcessingOutputs(worldState, receipts, maybeRequests)),
            if (parallelizedTxFound) Optional.of(nbParallelTx) else Optional.empty()
        )
    }

    protected open fun getTransactionProcessingResult(
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
        return transactionProcessor.processTransaction(
            blockUpdater,
            blockHeader,
            transaction,
            miningBeneficiary,
            OperationTracer.NO_TRACING,
            blockHashLookup,
            true,
            TransactionValidationParams.processingBlock(),
            privateMetadataUpdater,
            blobGasPrice
        )
    }

    protected fun hasAvailableBlockBudget(
        blockHeader: BlockHeader, transaction: Transaction, currentGasUsed: Long
    ): Boolean {
        val remainingGasBudget = blockHeader.gasLimit - currentGasUsed
        if (java.lang.Long.compareUnsigned(transaction.gasLimit, remainingGasBudget) > 0) {
            LOG.info(
                "Block processing error: transaction gas limit {} exceeds available block budget"
                        + " remaining {}. Block {} Transaction {}",
                transaction.gasLimit,
                remainingGasBudget,
                blockHeader.hash.toHexString(),
                transaction.hash.toHexString()
            )
            return false
        }

        return true
    }

    abstract fun rewardCoinbase(
        worldState: MutableWorldState?,
        header: BlockHeader?,
        ommers: List<BlockHeader>?,
        skipZeroBlockRewards: Boolean
    ): Boolean

    interface PreprocessingContext

    interface PreprocessingFunction {
        fun run(
            protocolContext: ProtocolContext?,
            privateMetadataUpdater: PrivateMetadataUpdater?,
            blockHeader: BlockHeader?,
            transactions: List<Transaction>?,
            miningBeneficiary: Address?,
            blockHashLookup: BlockHashLookup?,
            blobGasPrice: Wei?
        ): Optional<PreprocessingContext>

        class NoPreprocessing : PreprocessingFunction {
            override fun run(
                protocolContext: ProtocolContext?,
                privateMetadataUpdater: PrivateMetadataUpdater?,
                blockHeader: BlockHeader?,
                transactions: List<Transaction>?,
                miningBeneficiary: Address?,
                blockHashLookup: BlockHashLookup?,
                blobGasPrice: Wei?
            ): Optional<PreprocessingContext> {
                return Optional.empty()
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(AbstractBlockProcessor::class.java)

        const val MAX_GENERATION: Int = 6
    }
}
