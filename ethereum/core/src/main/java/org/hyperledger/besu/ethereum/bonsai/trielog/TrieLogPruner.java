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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogPruner {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogPruner.class);

  public static final long DEFAULT_RETENTION_THRESHOLD = 0L;
  public static int DEFAULT_PRUNING_LIMIT = 30_000;

  private final int pruningLimit;
  private final int loadingLimit;
  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private final long numBlocksToRetain;
  private static final Multimap<Long, byte[]> knownTrieLogKeysByDescendingBlockNumber =
      TreeMultimap.create(Comparator.reverseOrder(), Comparator.comparingInt(Arrays::hashCode));

  public TrieLogPruner(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final long numBlocksToRetain,
      final int pruningLimit) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.numBlocksToRetain = numBlocksToRetain;
    this.pruningLimit = pruningLimit;
    this.loadingLimit = pruningLimit; // same as pruningLimit for now
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
          Hash hash = Hash.wrap(Bytes32.wrap(blockHashAsBytes));
          final Optional<BlockHeader> header = blockchain.getBlockHeader(hash);
          if (header.isPresent()) {
            knownTrieLogKeysByDescendingBlockNumber.put(header.get().getNumber(), blockHashAsBytes);
            count.getAndIncrement();
          } else {
            // prune orphaned blocks (sometimes created during block production)
            rootWorldStateStorage.pruneTrieLog(blockHashAsBytes);
          }
        });
    LOG.atInfo().log("Loaded {} trie logs from database", count);
    pruneFromCache();
  }

  void cacheForLaterPruning(final long blockNumber, final byte[] trieLogKey) {
    LOG.atTrace()
        .setMessage("caching trie log for later pruning blockNumber {}; trieLogKey (blockHash) {}")
        .addArgument(blockNumber)
        .addArgument(Bytes.wrap(trieLogKey).toHexString())
        .log();
    knownTrieLogKeysByDescendingBlockNumber.put(blockNumber, trieLogKey);
  }

  void pruneFromCache() {

    final long retainAboveThisBlock = blockchain.getChainHeadBlockNumber() - numBlocksToRetain;
    final long retainAboveThisBlockOrFinalized =
        blockchain
            .getFinalized()
            .flatMap(blockchain::getBlockHeader)
            .map(ProcessableBlockHeader::getNumber)
            .map(finalizedBlock -> Math.min(finalizedBlock, retainAboveThisBlock))
            .orElse(retainAboveThisBlock);

    LOG.atTrace()
        .setMessage(
            "min((chainHeadNumber: {} - numBlocksToRetain: {}), finalized: {})) = retainAboveThisBlockOrFinalized: {}")
        .addArgument(blockchain.getChainHeadBlockNumber())
        .addArgument(numBlocksToRetain)
        .addArgument(
            () ->
                blockchain
                    .getFinalized()
                    .flatMap(blockchain::getBlockHeader)
                    .map(ProcessableBlockHeader::getNumber)
                    .orElse(null))
        .addArgument(retainAboveThisBlockOrFinalized)
        .log();

    final var pruneWindowEntries =
        knownTrieLogKeysByDescendingBlockNumber.asMap().entrySet().stream()
            .dropWhile((e) -> e.getKey() > retainAboveThisBlockOrFinalized)
            .limit(pruningLimit);

    final List<Long> blockNumbersToRemove = new ArrayList<>();

    final AtomicInteger count = new AtomicInteger();
    pruneWindowEntries.forEach(
        (e) -> {
          for (byte[] trieLogKey : e.getValue()) {
            rootWorldStateStorage.pruneTrieLog(trieLogKey);
            count.getAndIncrement();
          }
          blockNumbersToRemove.add(e.getKey());
        });

    blockNumbersToRemove.forEach(knownTrieLogKeysByDescendingBlockNumber::removeAll);
    LOG.atTrace()
        .setMessage("pruned {} trie logs for blocks {}")
        .addArgument(count)
        .addArgument(blockNumbersToRemove)
        .log();
    LOG.atDebug()
        .setMessage("pruned {} trie logs from {} blocks")
        .addArgument(count)
        .addArgument(blockNumbersToRemove.size())
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
      super(rootWorldStateStorage, blockchain, numBlocksToRetain, pruningLimit);
    }

    @Override
    public void initialize() {
      // no-op
    }

    @Override
    void cacheForLaterPruning(final long blockNumber, final byte[] trieLogKey) {
      // no-op
    }

    @Override
    void pruneFromCache() {
      // no-op
    }
  }
}
