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

package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogPruner {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogPruner.class);

  public static final long DEFAULT_RETENTION_THRESHOLD = 512L;
  public static final long MINIMUM_RETENTION_THRESHOLD = DEFAULT_RETENTION_THRESHOLD;
  public static int DEFAULT_PRUNING_LIMIT = 30_000;

  private final int pruningLimit;
  private final int loadingLimit;
  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private final long numBlocksToRetain;
  private final boolean requireFinalizedBlock;

  private static final Multimap<Long, Hash> trieLogBlocksAndForksByDescendingBlockNumber =
      TreeMultimap.create(Comparator.reverseOrder(), Comparator.naturalOrder());

  public TrieLogPruner(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final long numBlocksToRetain,
      final int pruningLimit,
      final boolean requireFinalizedBlock) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.numBlocksToRetain = numBlocksToRetain;
    this.pruningLimit = pruningLimit;
    this.loadingLimit = pruningLimit; // same as pruningLimit for now
    this.requireFinalizedBlock = requireFinalizedBlock;
  }

  public void initialize() {
    preloadCache();
  }

  private void preloadCache() {
    LOG.atInfo()
        .setMessage("Loading first {} trie logs from database...")
        .addArgument(loadingLimit)
        .log();
    final Stream<byte[]> trieLogKeys = rootWorldStateStorage.streamTrieLogKeys(loadingLimit);
    final AtomicLong count = new AtomicLong();
    trieLogKeys.forEach(
        blockHashAsBytes -> {
          Hash blockHash = Hash.wrap(Bytes32.wrap(blockHashAsBytes));
          final Optional<BlockHeader> header = blockchain.getBlockHeader(blockHash);
          if (header.isPresent()) {
            trieLogBlocksAndForksByDescendingBlockNumber.put(header.get().getNumber(), blockHash);
            count.getAndIncrement();
          } else {
            // prune orphaned blocks (sometimes created during block production)
            rootWorldStateStorage.pruneTrieLog(blockHash);
          }
        });
    LOG.atInfo().log("Loaded {} trie logs from database", count);
    pruneFromCache();
  }

  void cacheForLaterPruning(final long blockNumber, final Hash blockHash) {
    LOG.atTrace()
        .setMessage("caching trie log for later pruning blockNumber {}; blockHash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    trieLogBlocksAndForksByDescendingBlockNumber.put(blockNumber, blockHash);
  }

  void pruneFromCache() {
    final long retainAboveThisBlock = blockchain.getChainHeadBlockNumber() - numBlocksToRetain;
    final Optional<Hash> finalized = blockchain.getFinalized();
    if (requireFinalizedBlock && finalized.isEmpty()) {
      LOG.debug("No finalized block present, skipping pruning");
      return;
    }

    final long retainAboveThisBlockOrFinalized =
        finalized
            .flatMap(blockchain::getBlockHeader)
            .map(ProcessableBlockHeader::getNumber)
            .map(finalizedBlock -> Math.min(finalizedBlock, retainAboveThisBlock))
            .orElse(retainAboveThisBlock);

    LOG.atTrace()
        .setMessage(
            "min((chainHeadNumber: {} - numBlocksToRetain: {}) = {}, finalized: {})) = retainAboveThisBlockOrFinalized: {}")
        .addArgument(blockchain::getChainHeadBlockNumber)
        .addArgument(numBlocksToRetain)
        .addArgument(retainAboveThisBlock)
        .addArgument(
            () ->
                finalized
                    .flatMap(blockchain::getBlockHeader)
                    .map(ProcessableBlockHeader::getNumber)
                    .orElse(null))
        .addArgument(retainAboveThisBlockOrFinalized)
        .log();

    final var pruneWindowEntries =
        trieLogBlocksAndForksByDescendingBlockNumber.asMap().entrySet().stream()
            .dropWhile((e) -> e.getKey() > retainAboveThisBlockOrFinalized)
            .limit(pruningLimit);

    final Multimap<Long, Hash> toRemove = ArrayListMultimap.create();

    final AtomicInteger count = new AtomicInteger();
    pruneWindowEntries.forEach(
        (e) -> {
          for (Hash blockHash : e.getValue()) {
            rootWorldStateStorage.pruneTrieLog(blockHash);
            count.getAndIncrement();
            toRemove.put(e.getKey(), blockHash);
          }
        });

    toRemove.keySet().forEach(trieLogBlocksAndForksByDescendingBlockNumber::removeAll);

    LOG.atTrace()
        .setMessage("pruned {} trie logs for blocks {}")
        .addArgument(count)
        .addArgument(toRemove)
        .log();
    LOG.atDebug()
        .setMessage("pruned {} trie logs from {} blocks")
        .addArgument(count)
        .addArgument(toRemove::size)
        .log();
  }

  public static TrieLogPruner noOpTrieLogPruner() {
    return new NoOpTrieLogPruner(null, null, 0, 0);
  }

  public static class NoOpTrieLogPruner extends TrieLogPruner {
    private NoOpTrieLogPruner(
        final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
        final Blockchain blockchain,
        final long numBlocksToRetain,
        final int pruningLimit) {
      super(rootWorldStateStorage, blockchain, numBlocksToRetain, pruningLimit, true);
    }

    @Override
    public void initialize() {
      // no-op
    }

    @Override
    void cacheForLaterPruning(final long blockNumber, final Hash blockHash) {
      // no-op
    }

    @Override
    void pruneFromCache() {
      // no-op
    }
  }
}
