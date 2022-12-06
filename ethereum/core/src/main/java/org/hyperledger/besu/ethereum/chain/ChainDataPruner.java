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
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChainDataPruner implements BlockAddedObserver {
  private static final Logger LOG = LoggerFactory.getLogger(ChainDataPruner.class);
  private final BlockchainStorage blockchainStorage;
  private final ChainDataPrunerStorage prunerStorage;
  private final long blocksToRetain;
  private final long pruningFrequency;
  private final ExecutorService pruningExecutor;
  private static final int MAX_PRUNING_WORKER = 16;

  public ChainDataPruner(
      final BlockchainStorage blockchainStorage,
      final ChainDataPrunerStorage prunerStorage,
      final long blocksToRetain,
      final long pruningFrequency) {
    this.blockchainStorage = blockchainStorage;
    this.prunerStorage = prunerStorage;
    this.blocksToRetain = blocksToRetain;
    this.pruningFrequency = pruningFrequency;
    this.pruningExecutor =
        new ThreadPoolExecutor(
            1,
            1,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAX_PRUNING_WORKER),
            new ThreadPoolExecutor.DiscardPolicy());
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event) {
    pruningExecutor.submit(
        () -> {
          final long blockNumber = event.getBlock().getHeader().getNumber();
          long currentPruningMark = prunerStorage.getPruningMark().orElse(blockNumber);
          if (blockNumber < currentPruningMark) {
            LOG.warn(
                "Block added event: "
                    + event
                    + " has a block number of "
                    + blockNumber
                    + " < pruning mark "
                    + currentPruningMark
                    + " which normally indicates the blocksToRetain is too small");
            return;
          }
          final KeyValueStorageTransaction tx = prunerStorage.startTransaction();
          final Collection<Hash> forkBlocks = prunerStorage.getForkBlocks(blockNumber);
          forkBlocks.add(event.getBlock().getHash());
          prunerStorage.setForkBlocks(tx, blockNumber, forkBlocks);
          final long newPruningMark = blockNumber - blocksToRetain;
          if (event.isNewCanonicalHead()
              && newPruningMark - currentPruningMark >= pruningFrequency) {
            long currentRetainedBlock = blockNumber - currentPruningMark;
            while (currentRetainedBlock > blocksToRetain) {
              LOG.debug("Pruning chain data with block height of " + currentPruningMark);
              pruneChainDataAtBlock(tx, currentPruningMark);
              currentPruningMark++;
              currentRetainedBlock = blockNumber - currentPruningMark;
            }
          }
          prunerStorage.setPruningMark(tx, currentPruningMark);
          tx.commit();
        });
  }

  private void pruneChainDataAtBlock(final KeyValueStorageTransaction tx, final long blockNumber) {
    final Collection<Hash> oldForkBlocks = prunerStorage.getForkBlocks(blockNumber);
    final BlockchainStorage.Updater updater = blockchainStorage.updater();
    for (Hash toPrune : oldForkBlocks) {
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
    updater.removeBlockHash(blockNumber);
    updater.commit();
    prunerStorage.removeForkBlocks(tx, blockNumber);
  }
}
