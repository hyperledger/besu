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
package org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.diffbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the archiving of historic state that is still needed to satisfy queries but
 * doesn't need to be in the main DB segment for. Doing so would degrade block-import performance
 * over time so we move state beyond a certain age (in blocks) to other DB segments, assuming there
 * is a more recent (i.e. changed) version of the state. If state is created once and never changed
 * it will remain in the primary DB segment(s).
 */
public class BonsaiArchiver implements BlockAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiver.class);

  private final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private final Consumer<Runnable> executeAsync;
  private static final int CATCHUP_LIMIT = 1000;
  private static final int DISTANCE_FROM_HEAD_BEFORE_ARCHIVING_OLD_STATE = 10;
  private final TrieLogManager trieLogManager;
  protected final MetricsSystem metricsSystem;
  protected final Counter archivedBlocksCounter;

  private final Map<Long, Hash> pendingBlocksToArchive =
      Collections.synchronizedMap(new TreeMap<>());

  // For logging progress. Saves doing a DB read just to record our progress
  final AtomicLong latestArchivedBlock = new AtomicLong(0);

  public BonsaiArchiver(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final Consumer<Runnable> executeAsync,
      final TrieLogManager trieLogManager,
      final MetricsSystem metricsSystem) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.executeAsync = executeAsync;
    this.trieLogManager = trieLogManager;
    this.metricsSystem = metricsSystem;

    archivedBlocksCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "archived_blocks_state_total",
            "Total number of blocks for which state has been archived");
  }

  private int loadNextCatchupBlocks() {
    Optional<Long> archivedBlocksHead = Optional.empty();

    Optional<Long> latestArchivedBlock = rootWorldStateStorage.getLatestArchivedBlock();

    if (latestArchivedBlock.isPresent()) {
      // Start from the next block after the most recently archived block
      archivedBlocksHead = Optional.of(latestArchivedBlock.get() + 1);
    } else {
      // Start from genesis block
      if (blockchain.getBlockHashByNumber(0).isPresent()) {
        archivedBlocksHead = Optional.of(0L);
      }
    }

    int preLoadedBlocks = 0;
    if (archivedBlocksHead.isPresent()) {
      Optional<Block> nextBlock = blockchain.getBlockByNumber(archivedBlocksHead.get());
      for (int i = 0; i < CATCHUP_LIMIT; i++) {
        if (nextBlock.isPresent()) {
          addToArchivingQueue(
              nextBlock.get().getHeader().getNumber(), nextBlock.get().getHeader().getHash());
          preLoadedBlocks++;
          nextBlock = blockchain.getBlockByNumber(nextBlock.get().getHeader().getNumber() + 1);
        } else {
          break;
        }
      }
      LOG.atInfo()
          .setMessage("Preloaded {} blocks from {} to move their state and storage to the archive")
          .addArgument(preLoadedBlocks)
          .addArgument(archivedBlocksHead.get())
          .log();
    }
    return preLoadedBlocks;
  }

  public long initialize() {
    // On startup there will be recent blocks whose state and storage hasn't been archived yet.
    // Pre-load them in blocks of CATCHUP_LIMIT ready for archiving state once enough new blocks
    // have
    // been added to the chain.
    long totalBlocksCaughtUp = 0;
    int catchupBlocksLoaded = CATCHUP_LIMIT;
    while (catchupBlocksLoaded >= CATCHUP_LIMIT) {
      catchupBlocksLoaded = loadNextCatchupBlocks();
      moveBlockStateToArchive();
      totalBlocksCaughtUp += catchupBlocksLoaded;
    }
    return totalBlocksCaughtUp;
  }

  public int getPendingBlocksCount() {
    return pendingBlocksToArchive.size();
  }

  public synchronized void addToArchivingQueue(final long blockNumber, final Hash blockHash) {
    LOG.atDebug()
        .setMessage(
            "Adding block to archiving queue for moving to cold storage, blockNumber {}; blockHash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    pendingBlocksToArchive.put(blockNumber, blockHash);
  }

  private synchronized void removeArchivedFromQueue(final Map<Long, Hash> archivedBlocks) {
    archivedBlocks.keySet().forEach(e -> pendingBlocksToArchive.remove(e));
  }

  // Move state and storage entries from their primary DB segments to their archive DB segments.
  // This is
  // intended to maintain good performance for new block imports by keeping the primary DB segments
  // to live state only. Returns the number of state and storage entries moved.
  public int moveBlockStateToArchive() {
    final long retainAboveThisBlock =
        blockchain.getChainHeadBlockNumber() - DISTANCE_FROM_HEAD_BEFORE_ARCHIVING_OLD_STATE;

    if (rootWorldStateStorage.getFlatDbMode().getVersion() == Bytes.EMPTY) {
      throw new IllegalStateException("DB mode version not set");
    }

    AtomicInteger archivedAccountStateCount = new AtomicInteger();
    AtomicInteger archivedAccountStorageCount = new AtomicInteger();

    // Typically we will move all storage and state for a single block i.e. when a new block is
    // imported, move state for block-N. There are cases where we catch-up and move old state
    // for a number of blocks so we may iterate over a number of blocks archiving their state,
    // not just a single one.
    final SortedMap<Long, Hash> blocksToArchive;
    synchronized (this) {
      blocksToArchive = new TreeMap<>();
      pendingBlocksToArchive.entrySet().stream()
          .filter(
              (e) -> blocksToArchive.size() <= CATCHUP_LIMIT && e.getKey() <= retainAboveThisBlock)
          .forEach(
              (e) -> {
                blocksToArchive.put(e.getKey(), e.getValue());
              });
    }

    if (blocksToArchive.size() > 0) {
      LOG.atDebug()
          .setMessage("Moving cold state to archive storage: {} to {} ")
          .addArgument(blocksToArchive.firstKey())
          .addArgument(blocksToArchive.lastKey())
          .log();

      // Determine which world state keys have changed in the last N blocks by looking at the
      // trie logs for the blocks. Then move the old keys to the archive segment (if and only if
      // they have changed)
      blocksToArchive
          .entrySet()
          .forEach(
              (block) -> {
                Hash blockHash = block.getValue();
                LOG.atDebug()
                    .setMessage("Archiving all account state for block {}")
                    .addArgument(block.getKey())
                    .log();
                Optional<TrieLog> trieLog = trieLogManager.getTrieLogLayer(blockHash);
                if (trieLog.isPresent()) {
                  trieLog
                      .get()
                      .getAccountChanges()
                      .forEach(
                          (address, change) -> {
                            // Move any previous state for this account
                            archivedAccountStateCount.addAndGet(
                                rootWorldStateStorage.archivePreviousAccountState(
                                    blockchain.getBlockHeader(
                                        blockchain.getBlockHeader(blockHash).get().getParentHash()),
                                    address.addressHash()));
                          });
                  LOG.atDebug()
                      .setMessage("Archiving all storage state for block {}")
                      .addArgument(block.getKey())
                      .log();
                  trieLog
                      .get()
                      .getStorageChanges()
                      .forEach(
                          (address, storageSlotKey) -> {
                            storageSlotKey.forEach(
                                (slotKey, slotValue) -> {
                                  // Move any previous state for this account
                                  archivedAccountStorageCount.addAndGet(
                                      rootWorldStateStorage.archivePreviousStorageState(
                                          blockchain.getBlockHeader(
                                              blockchain
                                                  .getBlockHeader(blockHash)
                                                  .get()
                                                  .getParentHash()),
                                          Bytes.concatenate(
                                              address.addressHash(), slotKey.getSlotHash())));
                                });
                          });
                }
                LOG.atDebug()
                    .setMessage("All account state and storage archived for block {}")
                    .addArgument(block.getKey())
                    .log();
                rootWorldStateStorage.setLatestArchivedBlock(block.getKey());
                archivedBlocksCounter.inc();

                // Update local var for logging progress
                latestArchivedBlock.set(block.getKey());
                if (latestArchivedBlock.get() % 100 == 0) {
                  // Log progress in case catching up causes there to be a large number of keys
                  // to move
                  LOG.atInfo()
                      .setMessage(
                          "archive progress: state up to block {} archived ({} behind chain head {})")
                      .addArgument(latestArchivedBlock.get())
                      .addArgument(blockchain.getChainHeadBlockNumber() - latestArchivedBlock.get())
                      .addArgument(blockchain.getChainHeadBlockNumber())
                      .log();
                }
              });

      LOG.atDebug()
          .setMessage(
              "finished moving state for blocks {} to {}. Archived {} account state entries, {} account storage entries")
          .addArgument(blocksToArchive.firstKey())
          .addArgument(latestArchivedBlock.get())
          .addArgument(archivedAccountStateCount.get())
          .addArgument(archivedAccountStorageCount.get())
          .log();

      removeArchivedFromQueue(blocksToArchive);
    }

    return archivedAccountStateCount.get() + archivedAccountStorageCount.get();
  }

  private final Lock archiveMutex = new ReentrantLock(true);

  @Override
  public void onBlockAdded(final BlockAddedEvent addedBlockContext) {
    final Hash blockHash = addedBlockContext.getBlock().getHeader().getBlockHash();
    final Optional<Long> blockNumber =
        Optional.of(addedBlockContext.getBlock().getHeader().getNumber());
    blockNumber.ifPresent(
        blockNum -> {
          addToArchivingQueue(blockNum, blockHash);

          // Since moving blocks can be done in batches we only want
          // one instance running at a time
          executeAsync.accept(
              () -> {
                if (archiveMutex.tryLock()) {
                  try {
                    moveBlockStateToArchive();
                  } finally {
                    archiveMutex.unlock();
                  }
                }
              });
        });
  }
}
