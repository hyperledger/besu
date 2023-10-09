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
 */
package org.hyperledger.besu.cli.subcommands.operator;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.PrintWriter;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** The Trie Log subcommand. */
@Command(
    name = "x-trie-log",
    description = "Manipulate trie logs",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      TrieLogSubCommand.DeleteTrieLog.class,
      TrieLogSubCommand.ListTrieLog.class,
      TrieLogSubCommand.CountTrieLog.class
    })
public class TrieLogSubCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogSubCommand.class);

  @Option(
      names = "--block",
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "The block",
      arity = "1..1")
  private String targetBlockHash = "";

  @ParentCommand private OperatorSubCommand parentCommand;

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

  private BesuController besuController;

  @Override
  public void run() {
    try {
      final PrintWriter out = spec.commandLine().getOut();

      besuController = createBesuController();

      // TODO SLD use TrieLogProvider instead?
      //      parentCommand.parentCommand.parentCommand.besuPluginService

      /* TODO SLD CacheWorldStorageManager vs BonsaiWorldStateProvider?
      this.trieLogManager =
          new CachedWorldStorageManager(
              this,
              blockchain,
              worldStateStorage,
              metricsSystem,
              maxLayersToLoad.orElse(RETAINED_LAYERS),
              pluginContext);
       */
      if (besuController.getProtocolContext().getWorldStateArchive()
          instanceof BonsaiWorldStateProvider) {
        Optional<? extends TrieLog> trieLogLayer =
            ((BonsaiWorldStateProvider) besuController.getProtocolContext().getWorldStateArchive())
                .getTrieLogManager()
                .getTrieLogLayer(Hash.fromHexString(targetBlockHash));
        if (trieLogLayer.isPresent()) {
          out.printf("result: %s", trieLogLayer.get());
        } else {
          out.printf("No trie log found for block hash %s", targetBlockHash);
        }
      } else {
        out.println("Subcommand only works with Bonsai");
      }

      //      KeyValueStorage trieLogStorage =
      // besuController.getStorageProvider().getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
      //      Optional<byte[]> bytes =
      // trieLogStorage.get(Bytes.fromHexString(targetBlockHash.toString()).toArrayUnsafe());
      //      LOG.atInfo().setMessage("result: {}")
      //              .addArgument(HexFormat.of().formatHex(bytes.orElse(new byte[0])))
      //              .log();
    } catch (final Exception e) {
      LOG.error("TODO SLD", e);
    }
  }

  private BesuController createBesuController() {
    return parentCommand.parentCommand.buildController();
  }

  @Command(
      name = "delete",
      description = "This command deletes the trie log stored under the specified block.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class DeleteTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    private BesuController besuController;

    @Override
    public void run() {
      final PrintWriter out = spec.commandLine().getOut();

      checkNotNull(parentCommand);

      besuController = parentCommand.createBesuController();

      WorldStateArchive worldStateArchive =
          besuController.getProtocolContext().getWorldStateArchive();

      if (worldStateArchive instanceof BonsaiWorldStateProvider) {
        boolean success =
            ((BonsaiWorldStateProvider) worldStateArchive)
                .getTrieLogManager()
                .deleteTrieLogLayer(Hash.fromHexString(parentCommand.targetBlockHash.toString()));

        //      KeyValueStorage trieLogStorage =
        // besuController.getStorageProvider().getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
        //      boolean success =
        // trieLogStorage.tryDelete((Bytes.fromHexString(parentCommand.targetBlockHash.toString()).toArrayUnsafe()));
        out.printf("success? %s", success);
      } else {
        out.println("Subcommand only works with Bonsai");
      }
    }
  }

  @Command(
      name = "list",
      description = "This command lists all the trie logs",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class ListTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    private BesuController besuController;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final PrintWriter out = spec.commandLine().getOut();

      besuController = parentCommand.createBesuController();

      WorldStateArchive worldStateArchive =
          besuController.getProtocolContext().getWorldStateArchive();
      final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();

      if (worldStateArchive instanceof BonsaiWorldStateProvider) {
        final KeyValueStorage trieLogStorage =
            besuController
                .getStorageProvider()
                .getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
        trieLogStorage
            .streamKeys()
            .map(Bytes32::wrap)
            .map(Bytes::toHexString)
            .map(Hash::fromHexString)
            .forEach(
                hash ->
                    out.printf(
                        "trieLog for hash %s block: %s",
                        hash,
                        blockchain
                            .getBlockHeader(hash)
                            .map(BlockHeader::getNumber)
                            .map(String::valueOf)
                            .orElse("")));
      } else {
        out.println("Subcommand only works with Bonsai");
      }
    }
  }

  @Command(
      name = "count",
      description = "This command counts all the trie logs",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class CountTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    private BesuController besuController;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final PrintWriter out = spec.commandLine().getOut();

      besuController = parentCommand.createBesuController();

      WorldStateArchive worldStateArchive =
          besuController.getProtocolContext().getWorldStateArchive();
      final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();

      if (worldStateArchive instanceof BonsaiWorldStateProvider) {
        final KeyValueStorage trieLogStorage =
            besuController
                .getStorageProvider()
                .getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
        final long totalCount = trieLogStorage.stream().count();

        final long canonicalCount =
            trieLogStorage
                .streamKeys()
                .map(Bytes32::wrap)
                .map(Bytes::toHexString)
                .map(Hash::fromHexString)
                .map(blockchain::getBlockHeader)
                .filter(Optional::isPresent)
                .count();
        out.printf("trieLog total count: %d; blockchain count: %d", totalCount, canonicalCount);
      } else {
        out.print("Subcommand only works with Bonsai");
      }
    }
  }
}
