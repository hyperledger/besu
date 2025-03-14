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

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_NETWORK_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.getDefaultBesuDataPath;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Transaction;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/** The purge pre-merge block data sub command */
@CommandLine.Command(
    name = "purge-pre-merge-blocks",
    description =
        "Purges all pre-merge blocks and associated transaction receipts, leaving only headers",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class PurgePreMergeBlockDataSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(PurgePreMergeBlockDataSubCommand.class);

  private static final List<NetworkName> SUPPORTED_NETWORKS =
      List.of(NetworkName.MAINNET, NetworkName.SEPOLIA);
  private static final long MAINNET_MERGE_BLOCK_NUMBER = 15_537_393;
  private static final long SEPOLIA_MERGE_BLOCK_NUMBER = 1_735_371;

  @CommandLine.ParentCommand private StorageSubCommand storageSubCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  @CommandLine.Option(
      names = {"--network"},
      paramLabel = MANDATORY_NETWORK_FORMAT_HELP,
      defaultValue = "MAINNET",
      description =
          "Synchronize against the indicated network, possible values are ${COMPLETION-CANDIDATES}."
              + " (default: ${DEFAULT-VALUE})")
  private NetworkName network = null;

  @CommandLine.Option(
      names = {"--data-path"},
      paramLabel = MANDATORY_PATH_FORMAT_HELP,
      description = "The path to Besu data directory (default: ${DEFAULT-VALUE})")
  final Path dataPath = getDefaultBesuDataPath(this);

  /** Default constructor */
  public PurgePreMergeBlockDataSubCommand() {}

  @Override
  public void run() {
    if (!SUPPORTED_NETWORKS.contains(network)) {
      LOG.error(
          "Unable to purge pre-merge blocks and transaction receipts for network: {}", network);
      return;
    }

    LOG.info("Purging pre-merge blocks and transaction receipts");
    storageSubCommand.besuCommand.setNetwork(network);
    try (BesuController controller =
        storageSubCommand.besuCommand.setupControllerBuilder().dataDirectory(dataPath).build()) {
      final long mergeBlockNumber = getMergeBlockNumber(network);

      MutableBlockchain mutableBlockchain = controller.getProtocolContext().getBlockchain();
      if (!(mutableBlockchain instanceof DefaultBlockchain)) {
        throw new RuntimeException(
            "Unable to purge pre-merge data from MutableBlockchain implementations other than DefaultBlockchain");
      }

      DefaultBlockchain blockchain = (DefaultBlockchain) mutableBlockchain;
      BlockchainStorage.Updater updater = blockchain.getBlockchainStorage().updater();

      long headerNumber = 0;
      do {
        Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(headerNumber);
        maybeHeader
            .map(BlockHeader::getBlockHash)
            .ifPresent(
                (h) -> {
                  updater.removeBlockBody(h);
                  updater.removeTransactionReceipts(h);
                  updater.removeTotalDifficulty(h);
                  blockchain
                      .getBlockBody(h)
                      .map((bb) -> bb.getTransactions())
                      .ifPresent(
                          (transactions) ->
                              transactions.stream()
                                  .map(Transaction::getHash)
                                  .forEach((th) -> updater.removeTransactionLocation(th)));
                });
        if (headerNumber % 10000 == 0) {
          LOG.info("{} block's data removed", headerNumber);
        }
      } while (++headerNumber < mergeBlockNumber);
      LOG.info("Done removing block data, committing removal changes");
      updater.commit();
      LOG.info("Done committing removal changes");
    } catch (Exception e) {
      LOG.error("Unexpected exception", e);
    }
  }

  private static long getMergeBlockNumber(final NetworkName network) {
    return switch (network) {
      case MAINNET -> MAINNET_MERGE_BLOCK_NUMBER;
      case SEPOLIA -> SEPOLIA_MERGE_BLOCK_NUMBER;
      default -> throw new RuntimeException("Unexpected network: " + network);
    };
  }
}
