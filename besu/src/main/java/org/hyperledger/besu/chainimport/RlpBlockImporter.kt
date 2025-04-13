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
package org.hyperledger.besu.chainimport

import com.google.common.base.MoreObjects
import com.google.common.base.Stopwatch
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.chain.MutableBlockchain
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions
import org.hyperledger.besu.ethereum.util.RawBlockIterator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

/** Tool for importing rlp-encoded block data from files.  */
class RlpBlockImporter
/** Default Constructor.  */
    : Closeable {
    private val blockBacklog = Semaphore(2)

    private val validationExecutor: ExecutorService = Executors.newCachedThreadPool()
    private val importExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private var cumulativeGas: Long = 0
    private var segmentGas: Long = 0
    private val cumulativeTimer: Stopwatch = Stopwatch.createUnstarted()
    private val segmentTimer: Stopwatch = Stopwatch.createUnstarted()

    /**
     * Imports blocks that are stored as concatenated RLP sections in the given file into Besu's block
     * storage.
     *
     * @param blocks Path to the file containing the blocks
     * @param besuController the BesuController that defines blockchain behavior
     * @param skipPowValidation Skip proof of work validation (correct mix hash and difficulty)
     * @return the import result
     * @throws IOException On Failure
     */
    @Throws(IOException::class)
    fun importBlockchain(
        blocks: Path?, besuController: BesuController, skipPowValidation: Boolean
    ): ImportResult {
        return importBlockchain(blocks, besuController, skipPowValidation, 0L, Long.MAX_VALUE)
    }

    /**
     * Import blockchain.
     *
     * @param blocks the blocks
     * @param besuController the besu controller
     * @param skipPowValidation the skip pow validation
     * @param startBlock the start block
     * @param endBlock the end block
     * @return the rlp block importer - import result
     * @throws IOException the io exception
     */
    @Throws(IOException::class)
    fun importBlockchain(
        blocks: Path?,
        besuController: BesuController,
        skipPowValidation: Boolean,
        startBlock: Long,
        endBlock: Long
    ): ImportResult {
        val protocolSchedule = besuController.protocolSchedule
        val context = besuController.protocolContext
        val blockchain = context.blockchain
        var count = 0
        val blockHeaderFunctions =
            ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)
        RawBlockIterator(blocks, blockHeaderFunctions).use { iterator ->
            var previousHeader: BlockHeader? = null
            var previousBlockFuture: CompletableFuture<Void?>? = null
            val threadedException = AtomicReference<Throwable>()
            while (iterator.hasNext()) {
                val block = iterator.next()
                val header = block.header
                val blockNumber = header.number
                if (blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER || blockNumber < startBlock || blockNumber >= endBlock) {
                    continue
                }
                if (blockchain.contains(header.hash)) {
                    continue
                }
                if (previousHeader == null) {
                    previousHeader = lookupPreviousHeader(blockchain, header)
                }
                val protocolSpec = protocolSchedule.getByBlockHeader(header)
                val lastHeader = previousHeader

                val validationFuture =
                    CompletableFuture.runAsync(
                        { validateBlock(protocolSpec, context, lastHeader, header, skipPowValidation) },
                        validationExecutor
                    )

                val extractingFuture =
                    CompletableFuture.runAsync { extractSignatures(block) }
                val calculationFutures = if (previousBlockFuture == null) {
                    extractingFuture
                } else {
                    CompletableFuture.allOf(extractingFuture, previousBlockFuture)
                }

                try {
                    do {
                        val t: Throwable = threadedException.get() as Exception
                        if (t != null) {
                            throw RuntimeException("Error importing block " + header.number, t)
                        }
                    } while (!blockBacklog.tryAcquire(1, TimeUnit.SECONDS))
                } catch (e: InterruptedException) {
                    LOG.error("Interrupted adding to backlog.", e)
                    break
                }
                previousBlockFuture =
                    validationFuture.runAfterBothAsync(
                        calculationFutures,
                        {
                            evaluateBlock(
                                context,
                                block,
                                header,
                                protocolSchedule.getByBlockHeader(header),
                                skipPowValidation
                            )
                        },
                        importExecutor
                    )
                previousBlockFuture.exceptionally(
                    Function<Throwable, Void?> { exception: Throwable ->
                        threadedException.set(exception)
                        null
                    })

                ++count
                previousHeader = header
            }
            previousBlockFuture?.join()
            logProgress(blockchain.chainHeadBlockNumber)
            return ImportResult(
                blockchain.chainHead.totalDifficulty, count
            )
        }
    }

    private fun extractSignatures(block: Block) {
        val futures: MutableList<CompletableFuture<Void>> =
            ArrayList(block.body.transactions.size)
        for (tx in block.body.transactions) {
            futures.add(CompletableFuture.runAsync({ tx.sender }, validationExecutor))
        }
        for (future in futures) {
            future.join()
        }
    }

    private fun validateBlock(
        protocolSpec: ProtocolSpec,
        context: ProtocolContext,
        previousHeader: BlockHeader?,
        header: BlockHeader,
        skipPowValidation: Boolean
    ) {
        val blockHeaderValidator = protocolSpec.blockHeaderValidator
        val validHeader =
            blockHeaderValidator.validateHeader(
                header,
                previousHeader,
                context,
                if (skipPowValidation)
                    HeaderValidationMode.LIGHT_DETACHED_ONLY
                else
                    HeaderValidationMode.DETACHED_ONLY
            )
        check(validHeader) { "Invalid header at block number " + header.number + "." }
    }

    private fun evaluateBlock(
        context: ProtocolContext,
        block: Block,
        header: BlockHeader,
        protocolSpec: ProtocolSpec,
        skipPowValidation: Boolean
    ) {
        try {
            cumulativeTimer.start()
            segmentTimer.start()
            val blockImporter = protocolSpec.blockImporter
            val blockImported =
                blockImporter.importBlock(
                    context,
                    block,
                    if (skipPowValidation)
                        HeaderValidationMode.LIGHT_SKIP_DETACHED
                    else
                        HeaderValidationMode.SKIP_DETACHED,
                    if (skipPowValidation) HeaderValidationMode.LIGHT else HeaderValidationMode.FULL
                )
            check(blockImported.isImported) { "Invalid block at block number " + header.number + "." }
        } finally {
            blockBacklog.release()
            cumulativeTimer.stop()
            segmentTimer.stop()
            val thisGas = block.header.gasUsed
            cumulativeGas += thisGas
            segmentGas += thisGas
            if (header.number % SEGMENT_SIZE == 0L) {
                logProgress(header.number)
            }
        }
    }

    private fun logProgress(blockNum: Long) {
        val elapseMicros = segmentTimer.elapsed(TimeUnit.MICROSECONDS)
        LOG.info(
            "Import at block {} / {} gas {} micros / Mgps {} segment {} cumulative",
            blockNum,
            segmentGas,
            elapseMicros,
            segmentGas / elapseMicros.toDouble(),
            cumulativeGas / cumulativeTimer.elapsed(TimeUnit.MICROSECONDS).toDouble()
        )
        segmentGas = 0
        segmentTimer.reset()
    }

    private fun lookupPreviousHeader(
        blockchain: MutableBlockchain, header: BlockHeader
    ): BlockHeader? {
        try {
            return blockchain
                .getBlockHeader(header.parentHash)
                .orElseThrow {
                    IllegalStateException(
                        String.format(
                            "Block %s does not connect to the existing chain. Current chain head %s",
                            header.number, blockchain.chainHeadBlockNumber
                        )
                    )
                }
        } catch (e: IllegalStateException) {
            LOG.info("Block {} does not connect to the existing chain.", header.number)
        }
        return null
    }

    override fun close() {
        validationExecutor.shutdownNow()
        try {
            validationExecutor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            LOG.error("Error shutting down validatorExecutor.", e)
        }

        importExecutor.shutdownNow()
        try {
            importExecutor.awaitTermination(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            LOG.error("Error shutting down importExecutor", e)
        }
    }

    /** The Import result.  */
    class ImportResult
    /**
     * Instantiates a new Import result.
     *
     * @param td the td
     * @param count the count
     */ internal constructor(
      /** The difficulty.  */
      @JvmField val td: Difficulty,
      /** The Count.  */
      @JvmField val count: Int
    ) {
        override fun toString(): String {
            return MoreObjects.toStringHelper(this).add("td", td).add("count", count).toString()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(RlpBlockImporter::class.java)

        private const val SEGMENT_SIZE: Long = 1000
    }
}
