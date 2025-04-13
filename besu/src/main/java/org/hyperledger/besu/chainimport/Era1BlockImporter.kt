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
package org.hyperledger.besu.chainimport

import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.core.*
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult
import org.hyperledger.besu.ethereum.mainnet.BodyValidationMode
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput
import org.hyperledger.besu.ethereum.rlp.RLPInput
import org.hyperledger.besu.util.era1.*
import org.hyperledger.besu.util.snappy.SnappyFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.FileInputStream
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.*

/**
 * Tool for importing era1-encoded block data, headers, and transaction receipts from era1 files.
 */
class Era1BlockImporter
/** Default Constructor.  */
    : Closeable {
    /**
     * Imports the blocks, headers, and transaction receipts from the file found at the supplied path
     *
     * @param controller The BesuController
     * @param path The path
     * @throws IOException IOException
     * @throws ExecutionException ExecutionException
     * @throws InterruptedException InterruptedException
     * @throws TimeoutException TimeoutException
     */
    @Throws(
        IOException::class,
        ExecutionException::class,
        InterruptedException::class,
        TimeoutException::class
    )
    fun importBlocks(controller: BesuController, path: Path) {
        val protocolSchedule = controller.protocolSchedule
        val blockHeaderFunctions =
            ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)
        val context = controller.protocolContext

        val reader = Era1Reader(SnappyFactory())

        val headersFutures: MutableList<Future<BlockHeader>> = ArrayList(
            ERA1_BLOCK_COUNT_MAX
        )
        val bodiesFutures: MutableList<Future<BlockBody>> = ArrayList(
            ERA1_BLOCK_COUNT_MAX
        )
        val receiptsFutures: MutableList<Future<List<TransactionReceipt>>> =
            ArrayList(ERA1_BLOCK_COUNT_MAX)
        reader.read(
            FileInputStream(path.toFile()),
            object : Era1ReaderListener {
                override fun handleExecutionBlockHeader(
                    executionBlockHeader: Era1ExecutionBlockHeader
                ) {
                    headersFutures.add(
                        CompletableFuture.supplyAsync {
                            BlockHeader.readFrom(
                                BytesValueRLPInput(
                                    Bytes.wrap(executionBlockHeader.header), false
                                ),
                                blockHeaderFunctions
                            )
                        })
                }

                override fun handleExecutionBlockBody(executionBlockBody: Era1ExecutionBlockBody) {
                    bodiesFutures.add(
                        CompletableFuture.supplyAsync {
                            BlockBody.readWrappedBodyFrom(
                                BytesValueRLPInput(Bytes.wrap(executionBlockBody.block), false),
                                blockHeaderFunctions,
                                true
                            )
                        })
                }

                override fun handleExecutionBlockReceipts(
                    executionBlockReceipts: Era1ExecutionBlockReceipts
                ) {
                    receiptsFutures.add(
                        CompletableFuture.supplyAsync {
                            val input: RLPInput =
                                BytesValueRLPInput(
                                    Bytes.wrap(executionBlockReceipts.receipts), false
                                )
                            val receiptsForBlock: MutableList<TransactionReceipt> =
                                ArrayList()
                            input.readList { `in`: RLPInput? ->
                                receiptsForBlock.add(
                                    TransactionReceiptDecoder.readFrom(
                                        `in`
                                    )
                                )
                            }
                            receiptsForBlock
                        })
                }

                override fun handleBlockIndex(blockIndex: Era1BlockIndex) {
                    // not really necessary, do nothing
                }
            })

        LOG.info("Read {} blocks, now importing", headersFutures.size)

        var block: Block? = null
        for (i in headersFutures.indices) {
            val blockHeader = headersFutures[i][10, TimeUnit.SECONDS]
            val blockImporter =
                protocolSchedule.getByBlockHeader(blockHeader).blockImporter
            block = Block(
                blockHeader,
                bodiesFutures[i][10, TimeUnit.SECONDS]
            )

            val importResult =
                blockImporter.importBlockForSyncing(
                    context,
                    block,
                    receiptsFutures[i][10, TimeUnit.SECONDS],
                    HeaderValidationMode.NONE,
                    HeaderValidationMode.NONE,
                    BodyValidationMode.NONE,
                    false
                )
            if (importResult.status != BlockImportResult.BlockImportStatus.IMPORTED) {
                LOG.warn(
                    "Failed to import block {} due to {}",
                    blockHeader.number,
                    importResult.status
                )
            } else if (i % IMPORT_COUNT_FOR_LOG_UPDATE == 0) {
                LOG.info("{}/{} blocks imported", i, headersFutures.size)
            }
        }
        LOG.info("Done importing {} blocks", headersFutures.size)
    }

    @Throws(IOException::class)
    override fun close() {
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Era1BlockImporter::class.java)

        private const val ERA1_BLOCK_COUNT_MAX = 8192
        private const val IMPORT_COUNT_FOR_LOG_UPDATE = 1000
    }
}
