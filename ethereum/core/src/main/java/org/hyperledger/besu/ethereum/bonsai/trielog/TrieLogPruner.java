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

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogPruner {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogPruner.class);

  private final long loadingLimit;
  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private static final Multimap<Long, byte[]> knownTrieLogKeysByDescendingBlockNumber =
      TreeMultimap.create(Comparator.reverseOrder(), Comparator.comparingInt(Arrays::hashCode));

  public TrieLogPruner(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final long numBlocksToRetain) {
    this(rootWorldStateStorage, blockchain, numBlocksToRetain, numBlocksToRetain);
  }

  @VisibleForTesting
  TrieLogPruner(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final long numBlocksToRetain,
      final long pruningLimit) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.loadingLimit = Math.max(numBlocksToRetain, pruningLimit); // same as pruningLimit for now
  }

  public void initialize() {
    preloadCache();
  }

  private void preloadCache() {
    LOG.atInfo()
        .setMessage("Loading first {} trie logs from database...")
        .addArgument(loadingLimit)
        .log();
    final Stream<byte[]> trieLogs = rootWorldStateStorage.streamTrieLogs((int) loadingLimit);
    final AtomicLong count = new AtomicLong();
    trieLogs.forEach(
        hashAsBytes -> {
          Hash hash = Hash.wrap(Bytes32.wrap(hashAsBytes));
          final Optional<BlockHeader> header = blockchain.getBlockHeader(hash);
          if (header.isPresent()) {
            knownTrieLogKeysByDescendingBlockNumber.put(header.get().getNumber(), hashAsBytes);
            count.getAndIncrement();
          } else {
            // prune orphaned blocks (sometimes created during block production)
            rootWorldStateStorage.pruneTrieLog(hashAsBytes);
          }
        });
    LOG.atInfo().log("Loaded {} trie logs from database", count);
    printCache();
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
    printCache();
  }

  void printCache() {
    LOG.atInfo().log(
        () ->
            knownTrieLogKeysByDescendingBlockNumber.entries().stream()
                .map(
                    entry -> {
                      final Bytes32 hashBytes = Bytes32.wrap(entry.getValue());
                      final Hash hash = Hash.wrap(hashBytes);
                      return String.format(
                          "\nblockNumber: %s, trieLogKey: %s", entry.getKey(), hash.toHexString());
                    })
                .reduce((a, b) -> a + b)
                .orElse("empty"));
  }

  public static TrieLogPruner noOpTrieLogPruner() {
    return new NoOpTrieLogPruner(null, null, 0);
  }

  public static class NoOpTrieLogPruner extends TrieLogPruner {
    private NoOpTrieLogPruner(
        final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
        final Blockchain blockchain,
        final long numBlocksToRetain) {
      super(rootWorldStateStorage, blockchain, numBlocksToRetain);
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
