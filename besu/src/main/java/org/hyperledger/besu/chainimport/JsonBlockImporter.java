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
package org.hyperledger.besu.chainimport;

import org.hyperledger.besu.chainimport.internal.BlockData;
import org.hyperledger.besu.chainimport.internal.ChainData;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.config.PowAlgorithm;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tool for importing blocks with transactions from human-readable json. */
public class JsonBlockImporter {
  private static final Logger LOG = LoggerFactory.getLogger(JsonBlockImporter.class);

  private final ObjectMapper mapper;
  private final BesuController controller;

  public JsonBlockImporter(final BesuController controller) {
    this.controller = controller;
    mapper = new ObjectMapper();
    // Jdk8Module allows us to easily parse {@code Optional} values from json
    mapper.registerModule(new Jdk8Module());
    // Ignore casing of properties
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
  }

  public void importChain(final String chainJson) throws IOException {
    warnIfDatabaseIsNotEmpty();

    final ChainData chainData = mapper.readValue(chainJson, ChainData.class);

    final List<Block> importedBlocks = new ArrayList<>();
    for (final BlockData blockData : chainData.getBlocks()) {
      final BlockHeader parentHeader = getParentHeader(blockData, importedBlocks);
      final Block importedBlock = processBlockData(blockData, parentHeader);
      importedBlocks.add(importedBlock);
    }

    this.warnIfImportedBlocksAreNotOnCanonicalChain(importedBlocks);
  }

  private Block processBlockData(final BlockData blockData, final BlockHeader parentHeader) {
    LOG.info(
        "Preparing to import block at height {} (parent: {})",
        parentHeader.getNumber() + 1L,
        parentHeader.getHash());

    final WorldState worldState =
        controller
            .getProtocolContext()
            .getWorldStateArchive()
            .get(parentHeader.getStateRoot(), parentHeader.getHash())
            .get();
    final List<Transaction> transactions =
        blockData.streamTransactions(worldState).collect(Collectors.toList());

    final Block block = createBlock(blockData, parentHeader, transactions);
    assertAllTransactionsIncluded(block, transactions);
    importBlock(block);

    return block;
  }

  private Block createBlock(
      final BlockData blockData,
      final BlockHeader parentHeader,
      final List<Transaction> transactions) {
    final MiningCoordinator miner = controller.getMiningCoordinator();
    final GenesisConfigOptions genesisConfigOptions = controller.getGenesisConfigOptions();
    setOptionalFields(miner, blockData, genesisConfigOptions);

    // Some MiningCoordinator's (specific to consensus type) do not support block-level imports
    return miner
        .createBlock(parentHeader, transactions, Collections.emptyList())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unable to create block using current consensus engine: "
                        + genesisConfigOptions.getConsensusEngine()));
  }

  private void setOptionalFields(
      final MiningCoordinator miner,
      final BlockData blockData,
      final GenesisConfigOptions genesisConfig) {
    // Some fields can only be configured for ethash
    if (genesisConfig.getPowAlgorithm() != PowAlgorithm.UNSUPPORTED) {
      // For simplicity only set these for PoW consensus algorithms.
      // Other consensus algorithms use these fields for special purposes or ignore them.
      miner.setCoinbase(blockData.getCoinbase().orElse(Address.ZERO));
      miner.setExtraData(blockData.getExtraData().orElse(Bytes.EMPTY));
    } else if (blockData.getCoinbase().isPresent() || blockData.getExtraData().isPresent()) {
      // Fail if these fields are set for non-ethash chains
      final Stream.Builder<String> fields = Stream.builder();
      blockData.getCoinbase().map((c) -> "coinbase").ifPresent(fields::add);
      blockData.getExtraData().map((e) -> "extraData").ifPresent(fields::add);
      final String fieldsList = fields.build().collect(Collectors.joining(", "));
      throw new IllegalArgumentException(
          "Some fields ("
              + fieldsList
              + ") are unsupported by the current consensus engine: "
              + genesisConfig.getConsensusEngine());
    }
  }

  private void importBlock(final Block block) {
    final BlockImporter importer =
        controller
            .getProtocolSchedule()
            .getByBlockNumber(block.getHeader().getNumber())
            .getBlockImporter();

    final boolean imported =
        importer.importBlock(controller.getProtocolContext(), block, HeaderValidationMode.NONE);
    if (imported) {
      LOG.info(
          "Successfully created and imported block at height {} ({})",
          block.getHeader().getNumber(),
          block.getHash());
    } else {
      throw new IllegalStateException(
          "Newly created block " + block.getHeader().getNumber() + " failed validation.");
    }
  }

  private void assertAllTransactionsIncluded(
      final Block block, final List<Transaction> transactions) {
    if (transactions.size() != block.getBody().getTransactions().size()) {
      final int missingTransactions =
          transactions.size() - block.getBody().getTransactions().size();
      throw new IllegalStateException(
          "Unable to create block.  "
              + missingTransactions
              + " transaction(s) were found to be invalid.");
    }
  }

  private void warnIfDatabaseIsNotEmpty() {
    final long chainHeight =
        controller.getProtocolContext().getBlockchain().getChainHead().getHeight();
    if (chainHeight > BlockHeader.GENESIS_BLOCK_NUMBER) {
      LOG.warn(
          "Importing to a non-empty database with chain height {}.  This may cause imported blocks to be considered non-canonical.",
          chainHeight);
    }
  }

  private void warnIfImportedBlocksAreNotOnCanonicalChain(final List<Block> importedBlocks) {
    final List<BlockHeader> nonCanonicalHeaders =
        importedBlocks.stream()
            .map(Block::getHeader)
            .filter(
                header ->
                    controller
                        .getProtocolContext()
                        .getBlockchain()
                        .getBlockHeader(header.getNumber())
                        .map(c -> !c.equals(header))
                        .orElse(true))
            .collect(Collectors.toList());
    if (nonCanonicalHeaders.size() > 0) {
      final String blocksString =
          nonCanonicalHeaders.stream()
              .map(h -> "#" + h.getNumber() + " (" + h.getHash() + ")")
              .collect(Collectors.joining(", "));
      LOG.warn(
          "{} / {} imported blocks are not on the canonical chain: {}",
          nonCanonicalHeaders.size(),
          importedBlocks.size(),
          blocksString);
    }
  }

  private BlockHeader getParentHeader(final BlockData blockData, final List<Block> importedBlocks) {
    if (blockData.getParentHash().isPresent()) {
      final Hash parentHash = blockData.getParentHash().get();
      return controller
          .getProtocolContext()
          .getBlockchain()
          .getBlockHeader(parentHash)
          .orElseThrow(
              () -> new IllegalArgumentException("Unable to locate block parent at " + parentHash));
    }

    if (importedBlocks.size() > 0 && blockData.getNumber().isPresent()) {
      final long targetParentBlockNumber = blockData.getNumber().get() - 1L;
      Optional<BlockHeader> maybeHeader =
          importedBlocks.stream()
              .map(Block::getHeader)
              .filter(h -> h.getNumber() == targetParentBlockNumber)
              .findFirst();
      if (maybeHeader.isPresent()) {
        return maybeHeader.get();
      }
    }

    long blockNumber;
    if (blockData.getNumber().isPresent()) {
      blockNumber = blockData.getNumber().get() - 1L;
    } else if (importedBlocks.size() > 0) {
      // If there is no number or hash, import blocks in order
      blockNumber = importedBlocks.get(importedBlocks.size() - 1).getHeader().getNumber();
    } else {
      blockNumber = BlockHeader.GENESIS_BLOCK_NUMBER;
    }

    if (blockNumber < BlockHeader.GENESIS_BLOCK_NUMBER) {
      throw new IllegalArgumentException("Invalid block number: " + blockNumber + 1);
    }

    return controller
        .getProtocolContext()
        .getBlockchain()
        .getBlockHeader(blockNumber)
        .orElseThrow(
            () -> new IllegalArgumentException("Unable to locate block parent at " + blockNumber));
  }
}
