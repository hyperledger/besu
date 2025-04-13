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

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.chainimport.internal.BlockData
import org.hyperledger.besu.chainimport.internal.ChainData
import org.hyperledger.besu.config.GenesisConfigOptions
import org.hyperledger.besu.config.PowAlgorithm
import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.core.Block
import org.hyperledger.besu.ethereum.core.BlockHeader
import org.hyperledger.besu.ethereum.core.Transaction
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.stream.Collectors
import java.util.stream.Stream

/** Tool for importing blocks with transactions from human-readable json.  */
class JsonBlockImporter(private val controller: BesuController) {
    private val mapper = ObjectMapper()

    /**
     * Instantiates a new Json block importer.
     *
     * @param controller the controller
     */
    init {
        // Jdk8Module allows us to easily parse {@code Optional} values from json
        mapper.registerModule(Jdk8Module())
        // Ignore casing of properties
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
    }

    /**
     * Import chain.
     *
     * @param chainJson the chain json
     * @throws IOException the io exception
     */
    @Throws(IOException::class)
    fun importChain(chainJson: String?) {
        warnIfDatabaseIsNotEmpty()

        val chainData = mapper.readValue(chainJson, ChainData::class.java)

        val importedBlocks: MutableList<Block> = ArrayList()
        for (blockData in chainData.blocks) {
            val parentHeader = getParentHeader(blockData, importedBlocks)
            val importedBlock = processBlockData(blockData, parentHeader)
            importedBlocks.add(importedBlock)
        }

        this.warnIfImportedBlocksAreNotOnCanonicalChain(importedBlocks)
    }

    private fun processBlockData(blockData: BlockData, parentHeader: BlockHeader): Block {
        LOG.info(
            "Preparing to import block at height {} (parent: {})",
            parentHeader.number + 1L,
            parentHeader.hash
        )

        val worldState =
            controller
                .protocolContext
                .worldStateArchive[parentHeader.stateRoot, parentHeader.hash]
                .get()
        val transactions =
            blockData.streamTransactions(worldState).collect(Collectors.toList())

        val block = createBlock(blockData, parentHeader, transactions)
        assertAllTransactionsIncluded(block, transactions)
        importBlock(block)

        return block
    }

    private fun createBlock(
        blockData: BlockData,
        parentHeader: BlockHeader,
        transactions: List<Transaction>
    ): Block {
        val miner = controller.miningCoordinator
        val genesisConfigOptions = controller.genesisConfigOptions
        setOptionalFields(miner, blockData, genesisConfigOptions)

        // Some MiningCoordinator's (specific to consensus type) do not support block-level imports
        return miner
            .createBlock(parentHeader, transactions, emptyList())
            .orElseThrow {
                IllegalArgumentException(
                    "Unable to create block using current consensus engine: "
                            + genesisConfigOptions.consensusEngine
                )
            }
    }

    private fun setOptionalFields(
        miner: MiningCoordinator,
        blockData: BlockData,
        genesisConfig: GenesisConfigOptions
    ) {
        // Some fields can only be configured for ethash
        if (genesisConfig.powAlgorithm != PowAlgorithm.UNSUPPORTED) {
            // For simplicity only set these for PoW consensus algorithms.
            // Other consensus algorithms use these fields for special purposes or ignore them.
            miner.setCoinbase(blockData.coinbase.orElse(Address.ZERO))
            controller.miningParameters.setExtraData(blockData.extraData.orElse(Bytes.EMPTY))
        } else if (blockData.coinbase.isPresent || blockData.extraData.isPresent) {
            // Fail if these fields are set for non-ethash chains
            val fields = Stream.builder<String>()
            blockData.coinbase.map { c: Address? -> "coinbase" }.ifPresent { t: String ->
                fields.add(
                    t
                )
            }
            blockData.extraData.map { e: Bytes? -> "extraData" }.ifPresent { t: String ->
                fields.add(
                    t
                )
            }
            val fieldsList = fields.build().collect(Collectors.joining(", "))
            throw IllegalArgumentException(
                ("Some fields ("
                        + fieldsList
                        + ") are unsupported by the current consensus engine: "
                        + genesisConfig.consensusEngine)
            )
        }
    }

    private fun importBlock(block: Block) {
        val importer =
            controller.protocolSchedule.getByBlockHeader(block.header).blockImporter

        val importResult =
            importer.importBlock(controller.protocolContext, block, HeaderValidationMode.NONE)
        if (importResult.isImported) {
            LOG.info(
                "Successfully created and imported block at height {} ({})",
                block.header.number,
                block.hash
            )
        } else {
            throw IllegalStateException(
                "Newly created block " + block.header.number + " failed validation."
            )
        }
    }

    private fun assertAllTransactionsIncluded(
        block: Block, transactions: List<Transaction>
    ) {
        if (transactions.size != block.body.transactions.size) {
            val missingTransactions =
                transactions.size - block.body.transactions.size
            throw IllegalStateException(
                ("Unable to create block.  "
                        + missingTransactions
                        + " transaction(s) were found to be invalid.")
            )
        }
    }

    private fun warnIfDatabaseIsNotEmpty() {
        val chainHeight =
            controller.protocolContext.blockchain.chainHead.height
        if (chainHeight > BlockHeader.GENESIS_BLOCK_NUMBER) {
            LOG.warn(
                "Importing to a non-empty database with chain height {}.  This may cause imported blocks to be considered non-canonical.",
                chainHeight
            )
        }
    }

    private fun warnIfImportedBlocksAreNotOnCanonicalChain(importedBlocks: List<Block>) {
        val nonCanonicalHeaders =
            importedBlocks.stream()
                .map { obj: Block -> obj.header }
                .filter { header: BlockHeader ->
                    controller
                        .protocolContext
                        .blockchain
                        .getBlockHeader(header.number)
                        .map { c: BlockHeader -> c != header }
                        .orElse(true)
                }
                .collect(Collectors.toList())
        if (nonCanonicalHeaders.size > 0) {
            val blocksString =
                nonCanonicalHeaders.stream()
                    .map { h: BlockHeader -> "#" + h.number + " (" + h.hash + ")" }
                    .collect(Collectors.joining(", "))
            LOG.warn(
                "{} / {} imported blocks are not on the canonical chain: {}",
                nonCanonicalHeaders.size,
                importedBlocks.size,
                blocksString
            )
        }
    }

    private fun getParentHeader(blockData: BlockData, importedBlocks: List<Block>): BlockHeader {
        if (blockData.parentHash.isPresent) {
            val parentHash = blockData.parentHash.get()
            return controller
                .protocolContext
                .blockchain
                .getBlockHeader(parentHash)
                .orElseThrow { IllegalArgumentException("Unable to locate block parent at $parentHash") }
        }

        if (importedBlocks.size > 0 && blockData.number.isPresent) {
            val targetParentBlockNumber = blockData.number.get() - 1L
            val maybeHeader =
                importedBlocks.stream()
                    .map { obj: Block -> obj.header }
                    .filter { h: BlockHeader -> h.number == targetParentBlockNumber }
                    .findFirst()
            if (maybeHeader.isPresent) {
                return maybeHeader.get()
            }
        }
        val blockNumber = if (blockData.number.isPresent) {
            blockData.number.get() - 1L
        } else if (importedBlocks.size > 0) {
            // If there is no number or hash, import blocks in order
            importedBlocks[importedBlocks.size - 1].header.number
        } else {
            BlockHeader.GENESIS_BLOCK_NUMBER
        }

        require(blockNumber >= BlockHeader.GENESIS_BLOCK_NUMBER) { "Invalid block number: " + (blockNumber + 1) }

        return controller
            .protocolContext
            .blockchain
            .getBlockHeader(blockNumber)
            .orElseThrow { IllegalArgumentException("Unable to locate block parent at $blockNumber") }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(JsonBlockImporter::class.java)
    }
}
