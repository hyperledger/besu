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
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages the "freezing" of historic state that is still needed to satisfy queries but
 * doesn't need to be in the main DB segment for. Doing so would degrade block-import performance
 * over time so we move state beyond a certain age (in blocks) to other DB segments, assuming there
 * is a more recent (i.e. changed) version of the state. If state is created once and never changed
 * it will remain in the primary DB segment(s).
 */
public class BonsaiArchiveFreezer implements BlockAddedObserver {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveFreezer.class);

  private final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage;
  private final Blockchain blockchain;
  private final Consumer<Runnable> executeAsync;
  private static final int PRELOAD_LIMIT = 1000;
  private static final int DISTANCE_FROM_HEAD_BEFORE_FREEZING_OLD_STATE = 10;
  private final TrieLogManager trieLogManager;

  private final Multimap<Long, Hash> blocksToMoveToFreezer =
      TreeMultimap.create(Comparator.reverseOrder(), Comparator.naturalOrder());

  public BonsaiArchiveFreezer(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final Blockchain blockchain,
      final Consumer<Runnable> executeAsync,
      final TrieLogManager trieLogManager) {
    this.rootWorldStateStorage = rootWorldStateStorage;
    this.blockchain = blockchain;
    this.executeAsync = executeAsync;
    this.trieLogManager = trieLogManager;
  }

  public void initialize() {
    // On startup there will be recent blocks whose state and storage hasn't been archived yet.
    // Pre-load them ready for freezing state once enough new blocks have been added to the chain.
    Optional<Long> frozenBlocksHead = Optional.empty();

    Optional<Long> latestFrozenBlock = rootWorldStateStorage.getLatestArchiveFrozenBlock();

    if (latestFrozenBlock.isPresent()) {
      frozenBlocksHead = latestFrozenBlock;
    } else {
      // Start from genesis block
      if (blockchain.getBlockHashByNumber(0).isPresent()) {
        frozenBlocksHead = Optional.of(0L);
      }
    }

    if (frozenBlocksHead.isPresent()) {
      int preLoadedBlocks = 0;
      Optional<Block> nextBlock = blockchain.getBlockByNumber(frozenBlocksHead.get());
      for (int i = 0; i < PRELOAD_LIMIT; i++) {
        if (nextBlock.isPresent()) {
          addToFreezerQueue(
              nextBlock.get().getHeader().getNumber(), nextBlock.get().getHeader().getHash());
          preLoadedBlocks++;
          nextBlock = blockchain.getBlockByNumber(nextBlock.get().getHeader().getNumber() + 1);
        } else {
          break;
        }
      }
      LOG.atInfo()
          .setMessage("Preloaded {} blocks to move their state and storage to the archive freezer")
          .addArgument(preLoadedBlocks)
          .log();
    }

    // Start processing any backlog on startup - don't wait for a new block to be imported.
    moveBlockStateToFreezer();
  }

  public synchronized void addToFreezerQueue(final long blockNumber, final Hash blockHash) {
    LOG.atDebug()
        .setMessage(
            "Adding block to archive freezer queue for moving to cold storage, blockNumber {}; blockHash {}")
        .addArgument(blockNumber)
        .addArgument(blockHash)
        .log();
    blocksToMoveToFreezer.put(blockNumber, blockHash);
  }

  public synchronized int moveBlockStateToFreezer() {
    final long retainAboveThisBlock =
        blockchain.getChainHeadBlockNumber() - DISTANCE_FROM_HEAD_BEFORE_FREEZING_OLD_STATE;

    if (rootWorldStateStorage.getFlatDbMode().getVersion() == Bytes.EMPTY) {
      throw new IllegalStateException("DB mode version not set");
    }

    AtomicInteger frozenAccountStateCount = new AtomicInteger();
    AtomicInteger frozenAccountStorageCount = new AtomicInteger();

    LOG.atTrace()
        .setMessage(
            "Moving cold state to freezer storage (chainHeadNumber: {} - numberOfBlocksToKeepInWarmStorage: {}) = {}")
        .addArgument(blockchain::getChainHeadBlockNumber)
        .addArgument(DISTANCE_FROM_HEAD_BEFORE_FREEZING_OLD_STATE)
        .addArgument(retainAboveThisBlock)
        .log();

    final var accountsToMove =
        blocksToMoveToFreezer.asMap().entrySet().stream()
            .dropWhile((e) -> e.getKey() > retainAboveThisBlock);

    final Multimap<Long, Hash> accountStateFreezerActionsComplete = ArrayListMultimap.create();
    final Multimap<Long, Hash> accountStorageFreezerActionsComplete = ArrayListMultimap.create();

    // Determine which world state keys have changed in the last N blocks by looking at the
    // trie logs for the blocks. Then move the old keys to the freezer segment (if and only if they
    // have changed)
    accountsToMove
        .parallel()
        .forEach(
            (block) -> {
              for (Hash blockHash : block.getValue()) {
                Optional<TrieLog> trieLog = trieLogManager.getTrieLogLayer(blockHash);
                if (trieLog.isPresent()) {
                  trieLog
                      .get()
                      .getAccountChanges()
                      .forEach(
                          (address, change) -> {
                            // Move any previous state for this account
                            frozenAccountStateCount.addAndGet(
                                rootWorldStateStorage.freezePreviousAccountState(
                                    blockchain.getBlockHeader(
                                        blockchain.getBlockHeader(blockHash).get().getParentHash()),
                                    address.addressHash()));
                          });
                }
                accountStateFreezerActionsComplete.put(block.getKey(), blockHash);
              }
            });

    final var storageToMove =
        blocksToMoveToFreezer.asMap().entrySet().stream()
            .dropWhile((e) -> e.getKey() > retainAboveThisBlock);

    storageToMove
        .parallel()
        .forEach(
            (block) -> {
              for (Hash blockHash : block.getValue()) {
                Optional<TrieLog> trieLog = trieLogManager.getTrieLogLayer(blockHash);
                if (trieLog.isPresent()) {
                  trieLog
                      .get()
                      .getStorageChanges()
                      .forEach(
                          (address, storageSlotKey) -> {
                            storageSlotKey.forEach(
                                (slotKey, slotValue) -> {
                                  // Move any previous state for this account
                                  frozenAccountStorageCount.addAndGet(
                                      rootWorldStateStorage.freezePreviousStorageState(
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
                accountStorageFreezerActionsComplete.put(block.getKey(), blockHash);
              }
            });

    // For us to consider all state and storage changes for a block complete, it must have been
    // recorded in both accountState and accountStorage lists. If only one finished we need to try
    // freezing state/storage for that block again on the next loop
    AtomicInteger frozenBlocksCompleted = new AtomicInteger();
    accountStateFreezerActionsComplete
        .keySet()
        .forEach(
            (b) -> {
              if (accountStorageFreezerActionsComplete.containsKey(b)) {
                frozenBlocksCompleted.getAndIncrement();
                rootWorldStateStorage.setLatestArchiveFrozenBlock(b);
                blocksToMoveToFreezer.removeAll(b);
              }
            });

    if (frozenAccountStateCount.get() > 0 || frozenAccountStorageCount.get() > 0) {
      LOG.atDebug()
          .setMessage("Froze {} account state entries, {} account storage entries for {} blocks")
          .addArgument(frozenAccountStateCount.get())
          .addArgument(frozenAccountStorageCount.get())
          .addArgument(frozenBlocksCompleted.get())
          .log();
    }

    return frozenBlocksCompleted.get();
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent addedBlockContext) {
    final Hash blockHash = addedBlockContext.getBlock().getHeader().getBlockHash();
    final Optional<Long> blockNumber =
        Optional.of(addedBlockContext.getBlock().getHeader().getNumber());
    blockNumber.ifPresent(
        blockNum ->
            executeAsync.accept(
                () -> {
                  addToFreezerQueue(blockNum, blockHash);
                  moveBlockStateToFreezer();
                }));
  }
}
