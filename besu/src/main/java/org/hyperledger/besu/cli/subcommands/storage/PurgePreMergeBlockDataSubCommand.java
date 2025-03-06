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

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

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

  private static final long MERGE_BLOCK_NUMBER = 15_537_393;

  @CommandLine.ParentCommand private StorageSubCommand storageSubCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  /** Default constructor */
  public PurgePreMergeBlockDataSubCommand() {}

  @Override
  public void run() {
    LOG.info("Purging pre-merge blocks and transaction receipts");
    try (BesuController controller = storageSubCommand.besuCommand.buildController()) {
      MutableBlockchain mutableBlockchain = controller.getProtocolContext().getBlockchain();
      if (!(mutableBlockchain instanceof DefaultBlockchain)) {
        throw new RuntimeException(
            "Unable to purge pre-merge data from MutableBlockchain implementations other than DefaultBlockchain");
      }

      DefaultBlockchain blockchain = (DefaultBlockchain) mutableBlockchain;
      BlockchainStorage blockchainStorage = blockchain.getBlockchainStorage();
      BlockchainStorage.Updater updater = blockchainStorage.updater();

      long headerNumber = 0;
      do {
        Optional<BlockHeader> maybeHeader = blockchain.getBlockHeader(headerNumber);
        if (maybeHeader.isEmpty()) {
          LOG.info("Removed all block data before block header {} was missing", headerNumber);
          break;
        }
        maybeHeader
            .map(BlockHeader::getBlockHash)
            .ifPresent(
                (h) -> {
                  updater.removeBlockBody(h);
                  updater.removeTransactionReceipts(h);
                  updater.removeTotalDifficulty(h);
                });
        if (headerNumber % 10000 == 0) {
          LOG.info("{} block's data removed", headerNumber);
        }
      } while (++headerNumber < MERGE_BLOCK_NUMBER);
      LOG.info("Done removing block data, committing removal changes");
      updater.commit();
      LOG.info("Done committing removal changes");
    } catch (Exception e) {
      LOG.error("Unexpected exception", e);
    }
  }
}
