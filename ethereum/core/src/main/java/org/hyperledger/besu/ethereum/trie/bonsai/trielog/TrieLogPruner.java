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

package org.hyperledger.besu.ethereum.trie.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogPruner implements TrieLogEvent.TrieLogObserver {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogPruner.class);

  private final int pruningLimit;
  private final int loadingLimit;
  private final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private final Consumer<Runnable> executeAsync;
  private final long numBlocksToRetain;
  private final boolean requireFinalizedBlock;

  private final Multimap<Long, Hash> trieLogBlocksAndForksByDescendingBlockNumber =
      TreeMultimap.create(Comparator.reverseOrder(), Comparator.naturalOrder());

  public TrieLogPruner(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final Consumer<Runnable> executeAsync,
      final long numBlocksToRetain,
      final int pruningLimit,
      final boolean requireFinalizedBlock) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.executeAsync = executeAsync;
    this.numBlocksToRetain = numBlocksToRetain;
    this.pruningLimit = pruningLimit;
    this.loadingLimit = pruningLimit; // same as pruningLimit for now
    this.requireFinalizedBlock = requireFinalizedBlock;
  }

  public int initialize() {
    return preloadQueue();
  }

  private int preloadQueue() {
    LOG.atInfo()
        .setMessage("Loading first {} trie logs from database...")
        .addArgument(loadingLimit)
        .log();
    try (final Stream<byte[]> trieLogKeys = rootWorldStateStorage.streamTrieLogKeys(loadingLimit)) {
      final AtomicLong count = new AtomicLong();
      final AtomicLong orphansPruned = new AtomicLong();
      trieLogKeys.forEach(
          blockHashAsBytes -> {
            final Hash blockHash = Hash.wrap(Bytes32.wrap(blockHashAsBytes));
            final Optional<BlockHeader> header = blockchain.getBlockHeader(blockHash);
            if (header.isPresent()) {
              addToPruneQueue(header.get().getNumber(), blockHash);
              count.getAndIncrement();
            } else {
              // prune orphaned blocks (sometimes created during block production)
              rootWorldStateStorage.pruneTrieLog(blockHash);
              orphansPruned.getAndIncrement();
            }
          });
      LOG.atDebug().log("Pruned {} orphaned trie logs from database...", orphansPruned.intValue());
      LOG.atInfo().log("Loaded {} trie logs from database", count);
      return pruneFromQueue() + orphansPruned.intValue();
    } catch (Exception e) {
      LOG.error("Error loading trie logs from database, nothing pruned", e);
      return 0;
    }
  }

  void addToPruneQueue(final long blockNumber, final Hash blockHash) {
    LOG.atTrace()
        .setMessage("adding trie log to queue for later pruning blockNumber {}; blockHash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    trieLogBlocksAndForksByDescendingBlockNumber.put(blockNumber, blockHash);
  }

  int pruneFromQueue() {
    final long retainAboveThisBlock = blockchain.getChainHeadBlockNumber() - numBlocksToRetain;
    final Optional<Hash> finalized = blockchain.getFinalized();
    if (requireFinalizedBlock && finalized.isEmpty()) {
      LOG.debug("No finalized block present, skipping pruning");
      return 0;
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

    final Multimap<Long, Hash> wasPruned = ArrayListMultimap.create();

    pruneWindowEntries.forEach(
        (e) -> {
          for (Hash blockHash : e.getValue()) {
            if (rootWorldStateStorage.pruneTrieLog(blockHash)) {
              wasPruned.put(e.getKey(), blockHash);
            }
          }
        });

    wasPruned.keySet().forEach(trieLogBlocksAndForksByDescendingBlockNumber::removeAll);

    LOG.atTrace()
        .setMessage("pruned {} trie logs for blocks {}")
        .addArgument(wasPruned::size)
        .addArgument(wasPruned)
        .log();
    LOG.atDebug()
        .setMessage("pruned {} trie logs from {} blocks")
        .addArgument(wasPruned::size)
        .addArgument(() -> wasPruned.keySet().size())
        .log();

    return wasPruned.size();
  }

  @Override
  public void onTrieLogAdded(final TrieLogEvent event) {
    if (TrieLogEvent.Type.ADDED.equals(event.getType())) {
      final Hash blockHash = event.layer().getBlockHash();
      final Optional<Long> blockNumber = event.layer().getBlockNumber();
      blockNumber.ifPresent(
          blockNum ->
              executeAsync.accept(
                  () -> {
                    addToPruneQueue(blockNum, blockHash);
                    pruneFromQueue();
                  }));
    }
  }
}
