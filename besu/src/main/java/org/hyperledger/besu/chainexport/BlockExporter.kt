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
package org.hyperledger.besu.chainexport

import com.google.common.base.Preconditions
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/** Besu Block Export Util.  */
abstract class BlockExporter
/**
 * Instantiates a new Block exporter.
 *
 * @param blockchain the blockchain
 */ protected constructor(private val blockchain: Blockchain) {
    /**
     * Export blocks that are stored in Besu's block storage.
     *
     * @param outputFile the path at which to save the exported block data
     * @param maybeStartBlock the starting index of the block list to export (inclusive)
     * @param maybeEndBlock the ending index of the block list to export (exclusive), if not specified
     * a single block will be export
     * @throws IOException if an I/O error occurs while writing data to disk
     */
    @Throws(IOException::class)
    fun exportBlocks(
        outputFile: File,
        maybeStartBlock: Optional<Long>,
        maybeEndBlock: Optional<Long>
    ) {
        // Get range to export

        val startBlock = maybeStartBlock.orElse(BlockHeader.GENESIS_BLOCK_NUMBER)
        val endBlock = maybeEndBlock.orElse(blockchain.chainHeadBlockNumber + 1L)
        Preconditions.checkArgument(startBlock >= 0 && endBlock >= 0, "Start and end blocks must be greater than 0.")
        Preconditions.checkArgument(startBlock < endBlock, "Start block must be less than end block")

        // Append to file if a range is specified
        val append = maybeStartBlock.isPresent
        val outputStream = FileOutputStream(outputFile, append)

        LOG.info(
            "Exporting blocks [{},{}) to file {} (appending: {})",
            startBlock,
            endBlock,
            outputFile.toString(),
            append.toString()
        )

        var blockNumber = 0L
        for (i in startBlock..<endBlock) {
            val maybeBlock = blockchain.getBlockByNumber(i)
            if (maybeBlock.isEmpty) {
                LOG.warn("Unable to export blocks [{} - {}).  Blocks not found.", i, endBlock)
                break
            }

            val block = maybeBlock.get()
            blockNumber = block.header.number
            if (blockNumber % 100 == 0L) {
                LOG.info("Export at block {}", blockNumber)
            }

            exportBlock(outputStream, block)
        }

        outputStream.close()
        LOG.info("Export complete at block {}", blockNumber)
    }

    /**
     * Export block.
     *
     * @param outputStream The FileOutputStream where the block will be exported
     * @param block The block to export
     * @throws IOException In case of an error while exporting.
     */
    @Throws(IOException::class)
    protected abstract fun exportBlock(outputStream: FileOutputStream?, block: Block?)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(BlockExporter::class.java)
    }
}
