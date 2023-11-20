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

package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogPruner;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes32;

/** Helper class for counting and pruning trie logs */
public class TrieLogHelper {

  static void countAndPrune(
      final PrintWriter out,
      final DataStorageConfiguration config,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain,
      final BesuController besuController) {
    TrieLogHelper.validatePruneConfiguration(config);

    final TrieLogCount count = getCount(rootWorldStateStorage, Integer.MAX_VALUE, blockchain);

    out.println("Counting trie logs before prune...");
    printCount(out, count);
    out.println();

    final int layersToRetain = (int) config.getUnstable().getBonsaiTrieLogRetentionThreshold();
    final int batchSize = config.getUnstable().getBonsaiTrieLogPruningLimit();
    final boolean isProofOfStake =
        besuController.getGenesisConfigOptions().getTerminalTotalDifficulty().isPresent();
    TrieLogPruner pruner =
        new TrieLogPruner(
            rootWorldStateStorage, blockchain, layersToRetain, batchSize, isProofOfStake);

    final int totalToPrune = count.total() - layersToRetain;
    out.printf(
        """
        Total to prune = %d (total) - %d (retention threshold) =
        => %d
        """,
        count.total(), layersToRetain, totalToPrune);
    final long numBatches = Math.max(totalToPrune / batchSize, 1);
    out.println();
    out.printf(
        "Estimated number of batches = max(%d (total to prune) / %d (batch size), 1) = %d\n",
        totalToPrune, batchSize, numBatches);
    out.println();

    int noProgressCounter = 0;
    int prevTotalNumberPruned = 0;
    int totalNumberPruned = 0;
    int numberPrunedInBatch;
    int batchNumber = 1;
    while (totalNumberPruned < totalToPrune) {
      out.printf(
          """
          Pruning batch %d
          -----------------
          """, batchNumber++);
      // do prune
      numberPrunedInBatch = pruner.initialize();

      out.printf("Number pruned in batch = %d \n", numberPrunedInBatch);
      totalNumberPruned += numberPrunedInBatch;
      out.printf(
          """
          Running total number pruned =
          => %d of %d
          """,
          totalNumberPruned, totalToPrune);

      if (totalNumberPruned == prevTotalNumberPruned) {
        if (noProgressCounter++ == 5) {
          out.println("No progress in 5 batches, exiting");
          return;
        }
      }

      prevTotalNumberPruned = totalNumberPruned;
      out.println();
    }
    out.println("Trie log prune complete!");
    out.println();

    out.println("Counting trie logs after prune...");
    TrieLogHelper.printCount(
        out, TrieLogHelper.getCount(rootWorldStateStorage, Integer.MAX_VALUE, blockchain));
  }

  private static void validatePruneConfiguration(final DataStorageConfiguration config) {
    checkArgument(
        config.getUnstable().getBonsaiTrieLogRetentionThreshold()
            >= MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD,
        String.format(
            "--Xbonsai-trie-log-retention-threshold minimum value is %d",
            MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit() > 0,
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must be greater than 0",
            config.getUnstable().getBonsaiTrieLogPruningLimit()));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit()
            > config.getUnstable().getBonsaiTrieLogRetentionThreshold(),
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must greater than --Xbonsai-trie-log-retention-threshold=%d",
            config.getUnstable().getBonsaiTrieLogPruningLimit(),
            config.getUnstable().getBonsaiTrieLogRetentionThreshold()));
  }

  static TrieLogCount getCount(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final int limit,
      final Blockchain blockchain) {
    final AtomicInteger total = new AtomicInteger();
    final AtomicInteger canonicalCount = new AtomicInteger();
    final AtomicInteger forkCount = new AtomicInteger();
    final AtomicInteger orphanCount = new AtomicInteger();
    rootWorldStateStorage
        .streamTrieLogKeys(limit)
        .map(Bytes32::wrap)
        .map(Hash::wrap)
        .forEach(
            hash -> {
              total.getAndIncrement();
              blockchain
                  .getBlockHeader(hash)
                  .ifPresentOrElse(
                      (header) -> {
                        long number = header.getNumber();
                        final Optional<BlockHeader> headerByNumber =
                            blockchain.getBlockHeader(number);
                        if (headerByNumber.isPresent()
                            && headerByNumber.get().getHash().equals(hash)) {
                          canonicalCount.getAndIncrement();
                        } else {
                          forkCount.getAndIncrement();
                        }
                      },
                      orphanCount::getAndIncrement);
            });

    return new TrieLogCount(total.get(), canonicalCount.get(), forkCount.get(), orphanCount.get());
  }

  static void printCount(final PrintWriter out, final TrieLogCount count) {
    out.printf(
        "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
        count.total, count.canonicalCount, count.forkCount, count.orphanCount);
  }

  record TrieLogCount(int total, int canonicalCount, int forkCount, int orphanCount) {}
}
