/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.chainexport;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Pantheon Block Export Util. */
public abstract class BlockExporter {
  private static final Logger LOG = LogManager.getLogger();
  private final Blockchain blockchain;

  protected BlockExporter(final Blockchain blockchain) {
    this.blockchain = blockchain;
  }

  /**
   * Export blocks that are stored in Pantheon's block storage.
   *
   * @param outputFile the path at which to save the exported block data
   * @param maybeStartBlock the starting index of the block list to export (inclusive)
   * @param maybeEndBlock the ending index of the block list to export (exclusive), if not specified
   *     a single block will be export
   * @throws IOException if an I/O error occurs while writing data to disk
   */
  public void exportBlocks(
      final File outputFile,
      final Optional<Long> maybeStartBlock,
      final Optional<Long> maybeEndBlock)
      throws IOException {

    // Get range to export
    final long startBlock = maybeStartBlock.orElse(BlockHeader.GENESIS_BLOCK_NUMBER);
    final long endBlock = maybeEndBlock.orElse(blockchain.getChainHeadBlockNumber() + 1L);
    checkArgument(startBlock >= 0 && endBlock >= 0, "Start and end blocks must be greater than 0.");
    checkArgument(startBlock < endBlock, "Start block must be less than end block");

    // Append to file if a range is specified
    final boolean append = maybeStartBlock.isPresent();
    FileOutputStream outputStream = new FileOutputStream(outputFile, append);

    LOG.info(
        "Exporting blocks [{},{}) to file {} (appending: {})",
        startBlock,
        endBlock,
        outputFile.toString(),
        Boolean.toString(append));

    long blockNumber = 0L;
    for (long i = startBlock; i < endBlock; i++) {
      Optional<Block> maybeBlock = blockchain.getBlockByNumber(i);
      if (maybeBlock.isEmpty()) {
        LOG.warn("Unable to export blocks [{} - {}).  Blocks not found.", i, endBlock);
        break;
      }

      final Block block = maybeBlock.get();
      blockNumber = block.getHeader().getNumber();
      if (blockNumber % 100 == 0) {
        LOG.info("Export at block {}", blockNumber);
      }

      exportBlock(outputStream, block);
    }

    outputStream.close();
    LOG.info("Export complete at block {}", blockNumber);
  }

  protected abstract void exportBlock(final FileOutputStream outputStream, final Block block)
      throws IOException;
}
