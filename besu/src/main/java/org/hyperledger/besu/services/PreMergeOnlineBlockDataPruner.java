/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A pre-merge block data pruner designed to be run "online", in a besu instance */
public class PreMergeOnlineBlockDataPruner implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(PreMergeOnlineBlockDataPruner.class);

  private static final List<NetworkName> SUPPORTED_NETWORKS =
      List.of(NetworkName.MAINNET, NetworkName.SEPOLIA);
  private static final long MAINNET_MERGE_BLOCK_NUMBER = 15_537_393;
  private static final long SEPOLIA_MERGE_BLOCK_NUMBER = 1_735_371;

  private final int preMergeBlockPrunerRange;
  private final long preMergeBlockPrunerSleepTime;
  private final NetworkName networkName;
  private final BlockchainStorage blockchainStorage;

  /**
   * Constructs a PreMergeOnlineBlockDataPruner with the supplied parameters
   *
   * @param preMergeBlockPrunerRange How many blocks to prune at a time
   * @param preMergeBlockPrunerSleepTime How long to sleep between blocks in milliseconds
   * @param networkName The network to target
   * @param blockchainStorage The blockchain storage from which to prune blocks
   */
  public PreMergeOnlineBlockDataPruner(
      final int preMergeBlockPrunerRange,
      final long preMergeBlockPrunerSleepTime,
      final NetworkName networkName,
      final BlockchainStorage blockchainStorage) {
    this.preMergeBlockPrunerRange = preMergeBlockPrunerRange;
    this.preMergeBlockPrunerSleepTime = preMergeBlockPrunerSleepTime;
    this.networkName = networkName;
    this.blockchainStorage = blockchainStorage;
  }

  @Override
  public void run() {
    if (!SUPPORTED_NETWORKS.contains(networkName)) {
      LOG.error(
          "Unable to prune pre-merge blocks and transaction receipts for network: {}", networkName);
      return;
    }
    final long mergeBlockNumber = getMergeBlockNumber(networkName);

    LOG.info("Pruning pre-merge blocks and transaction receipts");
    for (long startBlockNumber = 0;
        startBlockNumber < mergeBlockNumber;
        startBlockNumber += preMergeBlockPrunerRange) {
      BlockchainStorage.Updater updater = blockchainStorage.updater();
      long headerNumber = startBlockNumber;
      final long endBlockNumber =
          Math.min(startBlockNumber + preMergeBlockPrunerRange, mergeBlockNumber);
      do {
        blockchainStorage
            .getBlockHash(headerNumber)
            .filter((h) -> blockchainStorage.getBlockBody(h).isPresent())
            .ifPresent(
                (h) -> {
                  updater.removeTransactionReceipts(h);
                  updater.removeTotalDifficulty(h);
                  blockchainStorage
                      .getBlockBody(h)
                      .map((bb) -> bb.getTransactions())
                      .ifPresent(
                          (transactions) ->
                              transactions.stream()
                                  .map(Transaction::getHash)
                                  .forEach((th) -> updater.removeTransactionLocation(th)));
                  updater.removeBlockBody(h);
                });
      } while (++headerNumber < endBlockNumber);
      updater.commit();

      if (endBlockNumber % preMergeBlockPrunerRange * 1000 == 0) {
        LOG.info("Pruned up to block {}", endBlockNumber);
      }

      try {
        Thread.sleep(preMergeBlockPrunerSleepTime);
      } catch (InterruptedException e) {
        return;
      }
    }
    LOG.info("Pre-merge block pruning complete");
  }

  private static long getMergeBlockNumber(final NetworkName network) {
    return switch (network) {
      case MAINNET -> MAINNET_MERGE_BLOCK_NUMBER;
      case SEPOLIA -> SEPOLIA_MERGE_BLOCK_NUMBER;
      default -> throw new RuntimeException("Unexpected network: " + network);
    };
  }
}
