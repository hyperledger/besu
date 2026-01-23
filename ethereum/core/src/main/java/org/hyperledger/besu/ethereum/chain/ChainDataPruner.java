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
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.log.LogUtil;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainDataPruner implements BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(ChainDataPruner.class);
  private static final int LOG_PRE_MERGE_PRUNING_PROGRESS_REPEAT_DELAY_SECONDS = 300;

  public static final int MAX_PRUNING_THREAD_QUEUE_SIZE = 16;

  private final BlockchainStorage blockchainStorage;
  private final Runnable unsubscribeRunnable;
  private final ChainDataPrunerStorage prunerStorage;
  private final long mergeBlock;
  private final Mode mode;
  private final ChainPrunerConfiguration config;
  private final ExecutorService pruningExecutor;
  private final AtomicBoolean logPreMergePruningProgress = new AtomicBoolean(true);

  public ChainDataPruner(
      final BlockchainStorage blockchainStorage,
      final Runnable unsubscribeRunnable,
      final ChainDataPrunerStorage prunerStorage,
      final long mergeBlock,
      final Mode mode,
      final ChainPrunerConfiguration config,
      final ExecutorService pruningExecutor) {
    this.blockchainStorage = blockchainStorage;
    this.unsubscribeRunnable = unsubscribeRunnable;
    this.prunerStorage = prunerStorage;
    this.mergeBlock = mergeBlock;
    this.mode = mode;
    this.config = config;
    this.pruningExecutor = pruningExecutor;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    switch (mode) {
      case CHAIN_PRUNING -> chainPrunerAction(event);
      case PRE_MERGE_PRUNING -> {
        if (event.isNewCanonicalHead()) preMergePruningAction();
      }
    }
  }

  private void chainPrunerAction(final BlockAddedEvent event) {
    final long blockNumber = event.getHeader().getNumber();
    final long storedBlockPruningMark = prunerStorage.getPruningMark().orElse(0L);
    final long storedBalPruningMark = prunerStorage.getBalPruningMark().orElse(0L);

    validatePruningMarks(blockNumber, storedBlockPruningMark, storedBalPruningMark);
    recordForkBlock(event, blockNumber);

    if (!event.isNewCanonicalHead()) {
      return;
    }

    pruningExecutor.submit(
        () -> pruneChainAndBalData(blockNumber, storedBlockPruningMark, storedBalPruningMark));
  }

  private void validatePruningMarks(
      final long blockNumber, final long storedPruningMark, final long storedBalPruningMark) {
    if (config.isBlockPruningEnabled() && blockNumber < storedPruningMark) {
      LOG.warn(
          "Block number {} is less than pruning mark {} - chain-pruning-blocks-retained may be too small",
          blockNumber,
          storedPruningMark);
    }
    if (config.isBalPruningEnabled() && blockNumber < storedBalPruningMark) {
      LOG.warn(
          "Block number {} is less than BAL pruning mark {} - chain-pruning-bals-retained may be too small",
          blockNumber,
          storedBalPruningMark);
    }
  }

  private void recordForkBlock(final BlockAddedEvent event, final long blockNumber) {
    final KeyValueStorageTransaction tx = prunerStorage.startTransaction();
    final Collection<Hash> forkBlocks = prunerStorage.getForkBlocks(blockNumber);
    forkBlocks.add(event.getHeader().getHash());
    prunerStorage.setForkBlocks(tx, blockNumber, forkBlocks);
    tx.commit();
  }

  private void pruneChainAndBalData(
      final long blockNumber, final long storedBlockPruningMark, final long storedBalPruningMark) {

    final long blockPruningMark = blockNumber - config.chainPruningBlocksRetained();
    final long balPruningMark = blockNumber - config.chainPruningBalsRetained();

    final boolean shouldPruneBlock =
        config.isBlockPruningEnabled() && shouldPrune(blockPruningMark, storedBlockPruningMark);
    final boolean shouldPruneBal =
        config.isBalPruningEnabled() && shouldPrune(balPruningMark, storedBalPruningMark);

    if (!shouldPruneBlock && !shouldPruneBal) {
      return;
    }

    final KeyValueStorageTransaction pruningTransaction = prunerStorage.startTransaction();
    final BlockchainStorage.Updater updater = blockchainStorage.updater();

    long currentChainMark = storedBlockPruningMark;
    long currentBalMark = storedBalPruningMark;

    // Determine iteration range
    // When chain pruning is active, BAL is also active (mode ALL)
    // When only BAL pruning is active (mode BAL), we prune from storedBalPruningMark to
    // balPruningMark
    final long startBlock = shouldPruneBlock ? storedBlockPruningMark : storedBalPruningMark;
    final long endBlock = shouldPruneBlock ? blockPruningMark : balPruningMark;

    for (long blockNum = startBlock; blockNum <= endBlock; blockNum++) {
      // In mode ALL: prune chain data up to blockPruningMark, BAL data up to balPruningMark
      // In mode BAL: only prune BAL data up to balPruningMark
      final boolean pruneChainAtBlock = shouldPruneBlock && blockNum <= blockPruningMark;
      final boolean pruneBalAtBlock = shouldPruneBal && blockNum <= balPruningMark;

      if (!pruneChainAtBlock && !pruneBalAtBlock) {
        continue;
      }

      final Collection<Hash> forkBlocks = prunerStorage.getForkBlocks(blockNum);

      for (final Hash blockHash : forkBlocks) {
        if (pruneChainAtBlock) {
          LOG.debug("Pruning chain data at block {}", blockNum);
          removeChainData(updater, blockHash);
        }
        if (pruneBalAtBlock) {
          LOG.debug("Pruning BAL data at block {}", blockNum);
          updater.removeBlockAccessList(blockHash);
        }
      }

      if (pruneChainAtBlock) {
        updater.removeBlockHash(blockNum);
        prunerStorage.removeForkBlocks(pruningTransaction, blockNum);
        currentChainMark = blockNum;
      }

      if (pruneBalAtBlock) {
        currentBalMark = blockNum;
      }
    }

    updater.commit();
    prunerStorage.setPruningMark(pruningTransaction, currentChainMark);
    prunerStorage.setBalPruningMark(pruningTransaction, currentBalMark);
    pruningTransaction.commit();
  }

  private boolean shouldPrune(final long newMark, final long currentMark) {
    return (newMark - currentMark) >= config.chainPruningFrequency();
  }

  private void removeChainData(final BlockchainStorage.Updater updater, final Hash blockHash) {
    updater.removeBlockHeader(blockHash);
    updater.removeBlockBody(blockHash);
    updater.removeTransactionReceipts(blockHash);
    updater.removeTotalDifficulty(blockHash);
    removeTransactionLocations(updater, blockHash);
  }

  private void removeTransactionLocations(
      final BlockchainStorage.Updater updater, final Hash blockHash) {
    blockchainStorage
        .getBlockBody(blockHash)
        .ifPresent(
            blockBody ->
                blockBody
                    .getTransactions()
                    .forEach(t -> updater.removeTransactionLocation(t.getHash())));
  }

  private void preMergePruningAction() {
    pruningExecutor.submit(
        () -> {
          try {
            Thread.sleep(1000);
            final long storedPruningMark = prunerStorage.getPruningMark().orElse(1L);
            final long expectedNewPruningMark =
                Math.min(storedPruningMark + config.preMergePruningBlocksQuantity(), mergeBlock);
            LOG.debug(
                "Attempting to prune blocks {} to {}", storedPruningMark, expectedNewPruningMark);
            final KeyValueStorageTransaction pruningTransaction = prunerStorage.startTransaction();
            final BlockchainStorage.Updater updater = blockchainStorage.updater();
            for (long blockNumber = storedPruningMark;
                blockNumber < expectedNewPruningMark;
                blockNumber++) {
              blockchainStorage
                  .getBlockHash(blockNumber)
                  .ifPresent(
                      (blockHash) -> {
                        updater.removeBlockBody(blockHash);
                        updater.removeTransactionReceipts(blockHash);
                        blockchainStorage
                            .getBlockBody(blockHash)
                            .ifPresent(
                                blockBody ->
                                    blockBody
                                        .getTransactions()
                                        .forEach(
                                            t -> updater.removeTransactionLocation(t.getHash())));
                      });
            }
            updater.commit();
            prunerStorage.setPruningMark(pruningTransaction, expectedNewPruningMark);
            pruningTransaction.commit();
            LOG.debug("Pruned pre-merge blocks up to {}", expectedNewPruningMark);
            LogUtil.throttledLog(
                () -> LOG.info("Pruned pre-merge blocks up to {}", expectedNewPruningMark),
                logPreMergePruningProgress,
                LOG_PRE_MERGE_PRUNING_PROGRESS_REPEAT_DELAY_SECONDS);
            if (expectedNewPruningMark == mergeBlock) {
              LOG.info("Done pruning pre-merge blocks.");
              LOG.debug("Unsubscribing from block added event observation");
              unsubscribeRunnable.run();
            }
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
  }

  public enum Mode {
    CHAIN_PRUNING,
    PRE_MERGE_PRUNING
  }
}
