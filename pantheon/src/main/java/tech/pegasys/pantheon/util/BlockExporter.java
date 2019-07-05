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
package tech.pegasys.pantheon.util;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.MoreObjects;

/** Pantheon Block Export Util. */
public class BlockExporter {

  /**
   * Export blocks that are stored in Pantheon's block storage.
   *
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param <C> the consensus context type
   * @param startBlock the starting index of the block list to export (inclusive)
   * @param endBlock the ending index of the block list to export (exclusive), if not specified a
   *     single block will be export
   * @return the export result
   */
  public <C> ExportResult exportBlockchain(
      final PantheonController<C> pantheonController, final Long startBlock, final Long endBlock) {

    final ProtocolContext<C> context = pantheonController.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();

    final Long sanitizedEndBlock = sanitizedEndBlockIndex(startBlock, endBlock);

    final List<Block> blocks = new ArrayList<>();
    for (long currentBlockIndex = startBlock;
        currentBlockIndex < sanitizedEndBlock;
        currentBlockIndex += 1) {
      Optional<Hash> blockHashByNumber = blockchain.getBlockHashByNumber(currentBlockIndex);
      blockHashByNumber.ifPresent(hash -> blocks.add(blockchain.getBlockByHash(hash)));
    }

    final boolean allBlocksAreFound = blocks.size() == (sanitizedEndBlock - startBlock);

    return new ExportResult(blocks, allBlocksAreFound);
  }

  private Long sanitizedEndBlockIndex(final Long startBlock, final Long endBlock) {
    if (endBlock == null) {
      return startBlock + 1;
    } else {
      return endBlock;
    }
  }

  public static final class ExportResult {

    public final List<Block> blocks;

    public final boolean allBlocksAreFound;

    ExportResult(final List<Block> blocks, final boolean allBlocksAreFound) {
      this.blocks = blocks;
      this.allBlocksAreFound = allBlocksAreFound;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("blocks", blocks)
          .add("allBlocksAreFound", allBlocksAreFound)
          .toString();
    }
  }
}
