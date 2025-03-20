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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FinishPosSyncStep implements Consumer<List<BlockHeader>> {

  protected final ProtocolContext protocolContext;

  private static final Logger LOG = LoggerFactory.getLogger(FinishPosSyncStep.class);

  private final SortedSet<Long> sortedSet = new TreeSet<>();
  // key for the blockRanges map is the block number immediately following the range
  private final Map<Long, BlockHeaderRange> blockRanges = new HashMap<>();
  private final MutableBlockchain blockchain;
  private final Difficulty chainHeadDifficulty;

  private long nextLowestBlockNumber = 0;

  public FinishPosSyncStep(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final EthContext ethContext,
      final BlockHeader pivotHeader,
      final boolean transactionIndexingEnabled,
      final long startingBlock) {
    this.protocolContext = protocolContext;
    this.blockchain = ethContext.getBlockchain();
    final long chainHeadBlockNumber = blockchain.getChainHeadBlockNumber();
    this.nextLowestBlockNumber = startingBlock;
    if (chainHeadBlockNumber != 0) {
      final Optional<BlockHeader> chainHeadBlockHeader =
          blockchain.getBlockHeader(chainHeadBlockNumber);
      this.chainHeadDifficulty = blockchain.calculateTotalDifficulty(chainHeadBlockHeader.get());
    } else {
      this.chainHeadDifficulty = Difficulty.ZERO;
    }
  }

  @Override
  public void accept(final List<BlockHeader> blockHeaderRange) {
    LOG.atInfo()
        .setMessage("Next lowest block number: {}, lowest block number: {}")
        .addArgument(nextLowestBlockNumber)
        .addArgument(blockHeaderRange.getFirst().getNumber())
        .log();
    final BlockHeaderRange newRange =
        new BlockHeaderRange(
            blockHeaderRange.getFirst(),
            blockHeaderRange.getLast(),
            calculateRangeDifficulty(blockHeaderRange));
    final long rangeLowestBlockNumber = newRange.getLowestBlockHeader().getNumber();
    blockRanges.put(rangeLowestBlockNumber, newRange);
    sortedSet.add(rangeLowestBlockNumber);
    BlockHeader nextChainHead = null;
    BlockHeaderRange removedRange = null;
    int i = 0;
    while (nextLowestBlockNumber == sortedSet.first()) {
      final Long removed = sortedSet.removeFirst();
      removedRange = blockRanges.remove(removed);
      nextChainHead = removedRange.getHighestBlockHeader();
      this.chainHeadDifficulty.add(removedRange.getRangeDifficulty());
      i++;
    }
    if (i > 0) {
      nextLowestBlockNumber = removedRange.getHighestBlockHeader().getNumber() + 1;
      blockchain.unsafeSetChainHead(nextChainHead, this.chainHeadDifficulty);
      LOG.atInfo()
          .setMessage("New chain head set to {}")
          .addArgument(nextChainHead.getNumber())
          .log();
    }
  }

  private static Difficulty calculateRangeDifficulty(final List<BlockHeader> blockHeaderRange) {
    final Difficulty rangeDifficulty = Difficulty.ZERO;
    for (final BlockHeader blockHeader : blockHeaderRange) {
      rangeDifficulty.add(blockHeader.getDifficulty());
    }
    return rangeDifficulty;
  }

  static class BlockHeaderRange {
    private final BlockHeader lowestBlockHeader;
    private final BlockHeader highestBlockHeader;
    private final Difficulty rangeDifficulty;

    BlockHeaderRange(
        final BlockHeader lowestBlockHeader,
        final BlockHeader highestBlockHeader,
        final Difficulty rangeDifficulty) {
      this.lowestBlockHeader = lowestBlockHeader;
      this.highestBlockHeader = highestBlockHeader;
      this.rangeDifficulty = rangeDifficulty;
    }

    BlockHeader getLowestBlockHeader() {
      return lowestBlockHeader;
    }

    BlockHeader getHighestBlockHeader() {
      return highestBlockHeader;
    }

    Difficulty getRangeDifficulty() {
      return rangeDifficulty;
    }
  }
}
