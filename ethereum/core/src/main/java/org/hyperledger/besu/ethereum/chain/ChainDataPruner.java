/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainDataPruner implements BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(ChainDataPruner.class);

  // TODO: cleanup - pruningEnabled will not be required after
  // https://github.com/hyperledger/besu/pull/4703 is merged.
  private static boolean pruningEnabled = false;
  private final BlockchainStorage blockchainStorage;
  private final ChainDataPrunerStorage prunerStorage;
  private final long blocksToRetain;

  private final long pruningFrequency;

  public ChainDataPruner(
      final BlockchainStorage blockchainStorage,
      final KeyValueStorage storage,
      final long blocksToRetain,
      final long pruningFrequency) {
    this.blockchainStorage = blockchainStorage;
    this.prunerStorage = new ChainDataPrunerStorage(storage);
    this.blocksToRetain = blocksToRetain;
    this.pruningFrequency = pruningFrequency;
  }

  public static void enablePruning() {
    pruningEnabled = true;
  }

  public static boolean isPruningEnabled() {
    return pruningEnabled;
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    LOG.debug("New block added event: " + event);
    new Thread(
            () -> {
              synchronized (prunerStorage) {
                // Get pruning mark
                final long blockNumber = event.getBlock().getHeader().getNumber();
                long pruningMark = prunerStorage.getPruningMark().orElse(blockNumber);
                if (blockNumber < pruningMark) {
                  // Ignore and warn if block number < pruning mark, this normally indicates the
                  // blocksToRetain
                  // is too small.
                  LOG.warn(
                      "Block added event: "
                          + event
                          + " has a block number of "
                          + blockNumber
                          + " < pruning mark "
                          + pruningMark);
                  return;
                }
                // Append block into fork blocks.
                final KeyValueStorageTransaction tx = prunerStorage.startTransaction();
                final Collection<Hash> forkBlocks = prunerStorage.getForkBlocks(blockNumber);
                forkBlocks.add(event.getBlock().getHash());
                prunerStorage.setForkBlocks(tx, blockNumber, forkBlocks);
                // If a block is a new canonical head, start pruning.
                if (event.isNewCanonicalHead()
                    && blockNumber - blocksToRetain - pruningMark >= pruningFrequency) {
                  while (blockNumber - pruningMark >= blocksToRetain) {
                    LOG.debug("Pruning chain data at pruning mark: " + pruningMark);
                    pruneChainDataAtBlock(tx, pruningMark);
                    pruningMark++;
                  }
                }
                // Update pruning mark and commit
                prunerStorage.setPruningMark(tx, pruningMark);
                tx.commit();
              }
            })
        .start();
  }

  private void pruneChainDataAtBlock(final KeyValueStorageTransaction tx, final long blockNumber) {
    // Get a collection of old fork blocks that need to be pruned.
    final Collection<Hash> oldForkBlocks = prunerStorage.getForkBlocks(blockNumber);
    final BlockchainStorage.Updater updater = blockchainStorage.updater();
    for (Hash toPrune : oldForkBlocks) {
      // Prune block header, body, receipts, total difficulty and transaction locations.
      updater.removeBlockHeader(toPrune);
      updater.removeBlockBody(toPrune);
      updater.removeTransactionReceipts(toPrune);
      updater.removeTotalDifficulty(toPrune);
      blockchainStorage
          .getBlockBody(toPrune)
          .ifPresent(
              blockBody ->
                  blockBody
                      .getTransactions()
                      .forEach(t -> updater.removeTransactionLocation(t.getHash())));
    }
    // Prune canonical chain mapping and commit.
    updater.removeBlockHash(blockNumber);
    updater.commit();
    // Remove old fork blocks.
    prunerStorage.removeForkBlocks(tx, blockNumber);
  }
}
