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
package org.hyperledger.besu.services

import com.google.common.base.Preconditions
import org.hyperledger.besu.datatypes.BlobGas
import org.hyperledger.besu.datatypes.Hash
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceBlock.ChainUpdater
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.Transaction
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult
import org.hyperledger.besu.evm.worldstate.WorldUpdater
import org.hyperledger.besu.plugin.Unstable
import org.hyperledger.besu.plugin.data.BlockTraceResult
import org.hyperledger.besu.plugin.data.TransactionTraceResult
import org.hyperledger.besu.plugin.services.TraceService
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.LongStream

/** The Trace service implementation.  */
@Unstable
class TraceServiceImpl
/**
 * Instantiates a new TraceServiceImpl service.
 *
 * @param blockchainQueries the blockchainQueries
 * @param protocolSchedule the protocolSchedule
 */(private val blockchainQueries: BlockchainQueries, private val protocolSchedule: ProtocolSchedule) : TraceService {
    /**
     * Traces block
     *
     * @param blockNumber the block number to be traced
     * @param tracer an instance of OperationTracer
     */
    override fun traceBlock(
        blockNumber: Long, tracer: BlockAwareOperationTracer
    ): BlockTraceResult {
        return traceBlock(blockchainQueries.blockchain.getBlockByNumber(blockNumber), tracer)
    }

    /**
     * Traces block
     *
     * @param hash the block hash to be traced
     * @param tracer an instance of OperationTracer
     */
    override fun traceBlock(hash: Hash, tracer: BlockAwareOperationTracer): BlockTraceResult {
        return traceBlock(blockchainQueries.blockchain.getBlockByHash(hash), tracer)
    }

    private fun traceBlock(
        maybeBlock: Optional<Block>, tracer: BlockAwareOperationTracer
    ): BlockTraceResult {
        Preconditions.checkArgument(tracer != null)
        if (maybeBlock.isEmpty) {
            return BlockTraceResult.empty()
        }

        val results = trace(maybeBlock.get(), tracer)

        if (results.isEmpty) {
            return BlockTraceResult.empty()
        }

        val builder = BlockTraceResult.builder()

        val transactionProcessingResults = results.get()
        val transactions = maybeBlock.get().body.transactions
        for (i in transactionProcessingResults.indices) {
            val transactionProcessingResult =
                transactionProcessingResults[i]
            val transactionTraceResult =
                if (transactionProcessingResult.isInvalid)
                    TransactionTraceResult.error(
                        transactions[i].hash,
                        transactionProcessingResult.validationResult.errorMessage
                    )
                else
                    TransactionTraceResult.success(transactions[i].hash)

            builder.addTransactionTraceResult(transactionTraceResult)
        }

        return builder.build()
    }

    /**
     * Traces range of blocks
     *
     * @param fromBlockNumber the beginning of the range (inclusive)
     * @param toBlockNumber the end of the range (inclusive)
     * @param beforeTracing Function which performs an operation on a MutableWorldState before tracing
     * @param afterTracing Function which performs an operation on a MutableWorldState after tracing
     * @param tracer an instance of OperationTracer
     */
    override fun trace(
        fromBlockNumber: Long,
        toBlockNumber: Long,
        beforeTracing: Consumer<WorldUpdater>,
        afterTracing: Consumer<WorldUpdater>,
        tracer: BlockAwareOperationTracer
    ) {
        Preconditions.checkArgument(tracer != null)
        LOG.debug("Tracing from block {} to block {}", fromBlockNumber, toBlockNumber)
        val blockchain = blockchainQueries.blockchain
        val blocks =
            LongStream.rangeClosed(fromBlockNumber, toBlockNumber)
                .mapToObj { number: Long ->
                    blockchain
                        .getBlockByNumber(number)
                        .orElseThrow {
                            RuntimeException(
                                "Block not found $number"
                            )
                        }
                }
                .toList()
        Tracer.processTracing<List<TransactionProcessingResult>>(
            blockchainQueries,
            blocks.first().hash
        ) { traceableState: Tracer.TraceableState ->
            val worldStateUpdater = traceableState.updater()
            val chainUpdater = ChainUpdater(traceableState, worldStateUpdater)
            beforeTracing.accept(worldStateUpdater)
            val results: MutableList<TransactionProcessingResult> = ArrayList()
            blocks.forEach(Consumer { block: Block ->
                results.addAll(
                    trace(
                        blockchain,
                        block,
                        chainUpdater,
                        tracer
                    )
                )
            })
            afterTracing.accept(chainUpdater.nextUpdater)
            Optional.of<List<TransactionProcessingResult>>(results)
        }
    }

    private fun trace(
        block: Block, tracer: BlockAwareOperationTracer
    ): Optional<List<TransactionProcessingResult>> {
        LOG.debug("Tracing block {}", block.toLogString())
        val blockchain = blockchainQueries.blockchain

        val results =
            Tracer.processTracing(
                blockchainQueries,
                block.hash
            ) { traceableState: Tracer.TraceableState? ->
                Optional.of(
                    trace(blockchain, block, ChainUpdater(traceableState), tracer)
                )
            }

        return results
    }

    private fun trace(
        blockchain: Blockchain,
        block: Block,
        chainUpdater: ChainUpdater,
        tracer: BlockAwareOperationTracer
    ): List<TransactionProcessingResult> {
        val results: MutableList<TransactionProcessingResult> = ArrayList()
        val protocolSpec = protocolSchedule.getByBlockHeader(block.header)
        val transactionProcessor = protocolSpec.transactionProcessor
        val header = block.header
        val miningBeneficiary =
            protocolSpec.miningBeneficiaryCalculator.calculateBeneficiary(block.header)
        tracer.traceStartBlock(block.header, block.body, miningBeneficiary)

        block
            .body
            .transactions
            .forEach(
                Consumer { transaction: Transaction? ->
                    val maybeParentHeader =
                        blockchain.getBlockHeader(header.parentHash)
                    val blobGasPrice =
                        protocolSpec
                            .feeMarket
                            .blobGasPricePerGas(
                                maybeParentHeader
                                    .map { parent: BlockHeader? ->
                                        ExcessBlobGasCalculator.calculateExcessBlobGasForParent(
                                            protocolSpec,
                                            parent
                                        )
                                    }
                                    .orElse(BlobGas.ZERO))

                    val worldUpdater = chainUpdater.nextUpdater
                    val result =
                        transactionProcessor.processTransaction(
                            worldUpdater,
                            header,
                            transaction,
                            protocolSpec.miningBeneficiaryCalculator.calculateBeneficiary(header),
                            tracer,
                            protocolSpec
                                .blockHashProcessor
                                .createBlockHashLookup(blockchain, header),
                            false,
                            blobGasPrice
                        )
                    results.add(result)
                })

        tracer.traceEndBlock(block.header, block.body)

        return results
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TraceServiceImpl::class.java)
    }
}
