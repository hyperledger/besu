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
package org.hyperledger.besu.cli.subcommands.storage;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/** The prune pre-merge block data sub command */
@CommandLine.Command(
    name = "prune-pre-merge-blocks",
    description =
        "Prunes all pre-merge blocks and associated transaction receipts, leaving only headers",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class PrunePreMergeBlockDataSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(PrunePreMergeBlockDataSubCommand.class);

  private static final List<NetworkName> SUPPORTED_NETWORKS =
      List.of(NetworkName.MAINNET, NetworkName.SEPOLIA);
  private static final long MAINNET_MERGE_BLOCK_NUMBER = 15_537_393;
  private static final long SEPOLIA_MERGE_BLOCK_NUMBER = 1_735_371;

  private static final int DEFAULT_THREADS = 4;
  private static final int DEFAULT_PRUNE_RANGE_SIZE = 10000;

  @SuppressWarnings("unused")
  @CommandLine.ParentCommand
  private StorageSubCommand storageSubCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(
      names = {"--threads"},
      description =
          "Specifies the number of concurrent threads to use when pruning (default: ${DEFAULT-VALUE})")
  private final Integer threads = DEFAULT_THREADS;

  @CommandLine.Option(
      names = {"--prune-range-size"},
      description = "Specifies the size of block ranges to be pruned (default: ${DEFAULT-VALUE})")
  private final Integer pruneRangeSize = DEFAULT_PRUNE_RANGE_SIZE;

  /** Default constructor */
  public PrunePreMergeBlockDataSubCommand() {}

  @Override
  public void run() {
    final NetworkName network = storageSubCommand.besuCommand.getNetwork();
    final Path dataPath = storageSubCommand.besuCommand.dataDir();
    if (!SUPPORTED_NETWORKS.contains(network)) {
      LOG.error(
          "Unable to prune pre-merge blocks and transaction receipts for network: {}", network);
      return;
    }

    LOG.info(
        "Pruning pre-merge blocks and transaction receipts, network={}, data path={}",
        network,
        dataPath);
    final long mergeBlockNumber = getMergeBlockNumber(network);

    try (BesuController besuController = storageSubCommand.besuCommand.buildController()) {

      BlockchainStorage blockchainStorage =
          besuController
              .getStorageProvider()
              .createBlockchainStorage(
                  besuController.getProtocolSchedule(),
                  besuController.getStorageProvider().createVariablesStorage(),
                  besuController.getDataStorageConfiguration());

      try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
        for (long i = 0; i < mergeBlockNumber; i += pruneRangeSize) {
          final long headerNumber = i;
          executor.submit(
              () -> deleteBlockRange(headerNumber, mergeBlockNumber, blockchainStorage));
        }
      }
    }
  }

  private static long getMergeBlockNumber(final NetworkName network) {
    return switch (network) {
      case MAINNET -> MAINNET_MERGE_BLOCK_NUMBER;
      case SEPOLIA -> SEPOLIA_MERGE_BLOCK_NUMBER;
      default -> throw new RuntimeException("Unexpected network: " + network);
    };
  }

  private void deleteBlockRange(
      final long startBlockNumber,
      final long mergeBlockNumber,
      final BlockchainStorage blockchainStorage) {
    BlockchainStorage.Updater updater = blockchainStorage.updater();
    long headerNumber = startBlockNumber;
    final long endBlockNumber = Math.min(startBlockNumber + pruneRangeSize, mergeBlockNumber);
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
    LOG.info("Completed deletion of block range {} to {}", startBlockNumber, endBlockNumber);
  }
}
