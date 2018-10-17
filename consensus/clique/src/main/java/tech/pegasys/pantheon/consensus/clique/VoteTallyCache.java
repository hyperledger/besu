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
package tech.pegasys.pantheon.consensus.clique;

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class VoteTallyCache {

  private final Blockchain blockchain;
  private final EpochManager epochManager;
  private final CliqueVoteTallyUpdater voteTallyUpdater;

  private final Cache<Hash, VoteTally> voteTallyCache =
      CacheBuilder.newBuilder().maximumSize(100).build();

  public VoteTallyCache(
      final Blockchain blockchain,
      final CliqueVoteTallyUpdater voteTallyUpdater,
      final EpochManager epochManager) {
    checkNotNull(blockchain);
    checkNotNull(voteTallyUpdater);
    checkNotNull(epochManager);
    this.blockchain = blockchain;
    this.voteTallyUpdater = voteTallyUpdater;
    this.epochManager = epochManager;
  }

  public VoteTally getVoteTallyAtBlock(final BlockHeader header) {
    try {
      return voteTallyCache.get(header.getHash(), () -> populateCacheUptoAndIncluding(header));
    } catch (final ExecutionException ex) {
      throw new RuntimeException("Unable to determine a VoteTally object for the requested block.");
    }
  }

  private VoteTally populateCacheUptoAndIncluding(final BlockHeader start) {
    BlockHeader header = start;
    final Deque<BlockHeader> intermediateBlocks = new ArrayDeque<>();
    VoteTally voteTally = null;

    while (true) { // Will run into an epoch block (and thus a VoteTally) to break loop.
      voteTally = findMostRecentAvailableVoteTally(header, intermediateBlocks);
      if (voteTally != null) {
        break;
      }

      header =
          blockchain
              .getBlockHeader(header.getParentHash())
              .orElseThrow(
                  () ->
                      new NoSuchElementException(
                          "Supplied block was on a orphaned chain, unable to generate VoteTally."));
    }
    return constructMissingCacheEntries(intermediateBlocks, voteTally);
  }

  private VoteTally findMostRecentAvailableVoteTally(
      final BlockHeader header, final Deque<BlockHeader> intermediateBlockHeaders) {
    intermediateBlockHeaders.push(header);
    VoteTally voteTally = voteTallyCache.getIfPresent(header.getParentHash());
    if ((voteTally == null) && (epochManager.isEpochBlock(header.getNumber()))) {
      final CliqueExtraData extraData = CliqueExtraData.decode(header.getExtraData());
      voteTally = new VoteTally(extraData.getValidators());
    }

    return voteTally;
  }

  private VoteTally constructMissingCacheEntries(
      final Deque<BlockHeader> headers, final VoteTally tally) {
    while (!headers.isEmpty()) {
      final BlockHeader h = headers.pop();
      voteTallyUpdater.updateForBlock(h, tally);
      voteTallyCache.put(h.getHash(), tally.copy());
    }
    return tally;
  }
}
