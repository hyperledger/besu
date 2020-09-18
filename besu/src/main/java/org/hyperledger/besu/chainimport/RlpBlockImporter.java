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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.mainnet.BlockHeaderValidator;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.util.RawBlockIterator;

import java.io.IOException;
import java.nio.file.Path;

import com.google.common.base.MoreObjects;
import org.apache.logging.log4j.Logger;

/** Tool for importing rlp-encoded block data from files. */
public class RlpBlockImporter {
  private static final Logger LOG = getLogger();

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
  public RlpBlockImporter.ImportResult importBlockchain(
      final Path blocks, final BesuController besuController, final boolean skipPowValidation)
      throws IOException {
    return importBlockchain(blocks, besuController, skipPowValidation, 0L, Long.MAX_VALUE);
  }

  public RlpBlockImporter.ImportResult importBlockchain(
      final Path blocks,
      final BesuController besuController,
      final boolean skipPowValidation,
      final long startBlock,
      final long endBlock)
      throws IOException {
    final ProtocolSchedule protocolSchedule = besuController.getProtocolSchedule();
    final ProtocolContext context = besuController.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();
    int count = 0;
    try (final RawBlockIterator iterator =
        new RawBlockIterator(
            blocks,
            rlp ->
                BlockHeader.readFrom(
                    rlp, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)))) {
      BlockHeader previousHeader = null;
      while (iterator.hasNext()) {
        final Block block = iterator.next();
        final BlockHeader header = block.getHeader();
        final long blockNumber = header.getNumber();
        if (blockNumber == BlockHeader.GENESIS_BLOCK_NUMBER
            || blockNumber < startBlock
            || blockNumber >= endBlock) {
          continue;
        }
        if (blockNumber % 100 == 0) {
          LOG.info("Import at block {}", blockNumber);
        }
        if (blockchain.contains(header.getHash())) {
          continue;
        }
        if (previousHeader == null) {
          previousHeader = lookupPreviousHeader(blockchain, header);
        }
        final ProtocolSpec protocolSpec = protocolSchedule.getByBlockNumber(blockNumber);
        final BlockHeader lastHeader = previousHeader;

        validateBlock(protocolSpec, context, lastHeader, header, skipPowValidation);

        evaluateBlock(context, block, header, protocolSpec, skipPowValidation);

        ++count;
        previousHeader = header;
      }
      return new RlpBlockImporter.ImportResult(
          blockchain.getChainHead().getTotalDifficulty(), count);
    }
  }

  private void validateBlock(
      final ProtocolSpec protocolSpec,
      final ProtocolContext context,
      final BlockHeader previousHeader,
      final BlockHeader header,
      final boolean skipPowValidation) {
    final BlockHeaderValidator blockHeaderValidator = protocolSpec.getBlockHeaderValidator();
    final boolean validHeader =
        blockHeaderValidator.validateHeader(
            header,
            previousHeader,
            context,
            skipPowValidation
                ? HeaderValidationMode.LIGHT_DETACHED_ONLY
                : HeaderValidationMode.DETACHED_ONLY);
    if (!validHeader) {
      LOG.error("Invalid block at block number {}.", header.getNumber());
      throw new IllegalStateException("Invalid header at block number " + header.getNumber() + ".");
    }
  }

  private void evaluateBlock(
      final ProtocolContext context,
      final Block block,
      final BlockHeader header,
      final ProtocolSpec protocolSpec,
      final boolean skipPowValidation) {
    final BlockImporter blockImporter = protocolSpec.getBlockImporter();
    final boolean blockImported =
        blockImporter.importBlock(
            context,
            block,
            skipPowValidation
                ? HeaderValidationMode.LIGHT_SKIP_DETACHED
                : HeaderValidationMode.SKIP_DETACHED,
            skipPowValidation ? HeaderValidationMode.LIGHT : HeaderValidationMode.FULL);
    if (!blockImported) {
      LOG.error("Invalid block at block number {}.", header.getNumber());
      throw new IllegalStateException("Invalid block at block number " + header.getNumber() + ".");
    }
  }

  private BlockHeader lookupPreviousHeader(
      final MutableBlockchain blockchain, final BlockHeader header) {
    return blockchain
        .getBlockHeader(header.getParentHash())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Block %s does not connect to the existing chain. Current chain head %s",
                        header.getNumber(), blockchain.getChainHeadBlockNumber())));
  }

  public static final class ImportResult {

    public final Difficulty td;

    final int count;

    ImportResult(final Difficulty td, final int count) {
      this.td = td;
      this.count = count;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("td", td).add("count", count).toString();
    }
  }
}
