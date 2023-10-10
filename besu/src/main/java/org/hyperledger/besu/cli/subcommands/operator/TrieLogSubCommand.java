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
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;
import static org.hyperledger.besu.ethereum.bonsai.trielog.AbstractTrieLogManager.LOG_RANGE_LIMIT;

import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.trielog.AbstractTrieLogManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
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
      TrieLogSubCommand.CountTrieLog.class,
      TrieLogSubCommand.ListTrieLog.class,
      TrieLogSubCommand.DeleteTrieLog.class,
      TrieLogSubCommand.PruneTrieLog.class
    })
public class TrieLogSubCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogSubCommand.class);

  @Option(
      names = "--block",
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "The block",
      arity = "0..1")
  private String targetBlockHash = "";

  @Option(
      names = {"--from", "--from-block-number"},
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "Start of the block number range",
      arity = "0..1")
  private Long fromBlockNumber;

  @Option(
      names = {"--to", "--to-block-number"},
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "End of the block number range",
      arity = "0..1")
  private Long toBlockNumber;

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

      //            KeyValueStorage trieLogStorage =
      //
      // besuController.getStorageProvider().getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
      //            Optional<byte[]> bytes =
      //       trieLogStorage.get(Bytes.fromHexString(targetBlockHash.toString()).toArrayUnsafe());
      //            LOG.atInfo().setMessage("result: {}")
      //                    .addArgument(HexFormat.of().formatHex(bytes.orElse(new byte[0])))
      //                    .log();
    } catch (final Exception e) {
      LOG.error("TODO SLD", e);
      spec.commandLine().usage(System.out);
    }
  }

  private BesuController createBesuController() {
    return parentCommand.parentCommand.buildController();
  }

  private Stream<Long> rangeAsStream(final long fromBlockNumber, final long toBlockNumber) {
    if (Math.abs(toBlockNumber - fromBlockNumber) > LOG_RANGE_LIMIT) {
      throw new IllegalArgumentException("Requested Range too large");
    }
    long left = Math.min(fromBlockNumber, toBlockNumber);
    long right = Math.max(fromBlockNumber, toBlockNumber);
    return LongStream.range(left, right).boxed();
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
        out.println("list of trie logs:");
        out.println(
            "                    block hash (key)                               | block number");
        out.println(
            "___________________________________________________________________|__________________");

        Predicate<Hash> rangeFilter = (_ignore) -> true;
        if (parentCommand.fromBlockNumber != null && parentCommand.toBlockNumber != null) {

          final List<Hash> blockHashRange =
              parentCommand
                  .rangeAsStream(parentCommand.fromBlockNumber, parentCommand.toBlockNumber)
                  .map(blockchain::getBlockHeader)
                  .flatMap(Optional::stream)
                  .map(BlockHeader::getHash)
                  .toList();

          rangeFilter = blockHashRange::contains;
        }

        trieLogStorage
            .streamKeys()
            .map(Bytes32::wrap)
            .map(Bytes::toHexString)
            .map(Hash::fromHexString)
            .filter(rangeFilter)
            .forEach(
                hash ->
                    out.printf(
                        "%s | %s\n",
                        hash,
                        blockchain
                            .getBlockHeader(hash)
                            .map(
                                (header) -> {
                                  long number = header.getNumber();
                                  final Optional<BlockHeader> headerByNumber =
                                      blockchain.getBlockHeader(number);
                                  if (headerByNumber.isPresent()
                                      && headerByNumber.get().getHash().equals(hash)) {
                                    return String.valueOf(number);
                                  } else {
                                    return "fork of " + number;
                                  }
                                })
                            .orElse("not in blockchain")));
      } else {
        out.println("Subcommand only works with Bonsai");
      }
    }
  }

  @Command(
      name = "delete",
      description = "Deletes the trie logs stored under the specified hash or block number range.",
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
      final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();

      WorldStateArchive worldStateArchive =
          besuController.getProtocolContext().getWorldStateArchive();

      if (worldStateArchive instanceof BonsaiWorldStateProvider) {
        final String targetBlockHash = parentCommand.targetBlockHash;
        final Long fromBlockNumber = parentCommand.fromBlockNumber;
        final Long toBlockNumber = parentCommand.toBlockNumber;

        boolean success = false;
        if (!targetBlockHash.isEmpty()) {
          success =
              ((BonsaiWorldStateProvider) worldStateArchive)
                  .getTrieLogManager()
                  .deleteTrieLogLayer(Hash.fromHexString(targetBlockHash));
        } else if (fromBlockNumber != null && toBlockNumber != null) {

          final Stream<Hash> toDelete =
              parentCommand
                  .rangeAsStream(fromBlockNumber, toBlockNumber)
                  .map(blockchain::getBlockHeader)
                  .flatMap(Optional::stream)
                  .map(BlockHeader::getHash);

          success =
              toDelete.allMatch(
                  h ->
                      ((BonsaiWorldStateProvider) worldStateArchive)
                          .getTrieLogManager()
                          .deleteTrieLogLayer(h));

        } else {
          out.println("Please specify either --block or --fromBlockNumber and --toBlockNumber");
        }

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
      name = "prune",
      description =
          "This command prunes all trie logs below the specified block number, including orphaned trie logs.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class PruneTrieLog implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private TrieLogSubCommand parentCommand;

    @SuppressWarnings("unused")
    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec; // Picocli injects reference to command spec

    private BesuController besuController;

    @Option(
        names = {"--below", "--below-block-number"},
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description = "First block number to retain, prune below this number",
        arity = "0..1")
    private Long belowBlockNumber = AbstractTrieLogManager.RETAINED_LAYERS;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final PrintWriter out = spec.commandLine().getOut();

      printTrieLogDiskUsage(out);

      besuController = parentCommand.createBesuController();
      final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();

      WorldStateArchive worldStateArchive =
          besuController.getProtocolContext().getWorldStateArchive();

      if (worldStateArchive instanceof BonsaiWorldStateProvider) {
        if (belowBlockNumber != null) {

          final KeyValueStorage trieLogStorage =
              besuController
                  .getStorageProvider()
                  .getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);

          final AtomicInteger prunedCount = new AtomicInteger();
          trieLogStorage
              .streamKeys()
              .forEach(
                  hashAsBytes -> {
                    Hash hash = Hash.wrap(Bytes32.wrap(hashAsBytes));

                    final Optional<BlockHeader> header = blockchain.getBlockHeader(hash);
                    if (header.isEmpty()) { // TODO SLD what if we're still producing this block?
                      // Orphaned trie logs are neither in the canonical blockchain nor forks.
                      // Likely created during block production
                      recordResult(trieLogStorage.tryDelete(hashAsBytes), prunedCount, hash);
                    } else if (header.get().getNumber() < belowBlockNumber) {
                      // Prune canonical and fork trie logs below the block number
                      recordResult(trieLogStorage.tryDelete(hashAsBytes), prunedCount, hash);
                    } else {
                      LOG.atInfo().setMessage("Retain {}").addArgument(hash::toHexString).log();
                    }
                  });
          out.printf("Pruned %d trie logs\n", prunedCount.get());

          printTrieLogDiskUsage(out);
        } else {
          out.println("Please specify --belowBlockNumber");
        }

        //      KeyValueStorage trieLogStorage =
        // besuController.getStorageProvider().getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE);
        //      boolean success =
        // trieLogStorage.tryDelete((Bytes.fromHexString(parentCommand.targetBlockHash.toString()).toArrayUnsafe()));
      } else {
        out.println("Subcommand only works with Bonsai");
      }
    }

    private void printTrieLogDiskUsage(final PrintWriter out) {

      final String dbPath =
          parentCommand
              .parentCommand
              .parentCommand
              .dataDir()
              .toString()
              .concat("/")
              .concat(DATABASE_PATH);

      RocksDB.loadLibrary();
      Options options = new Options();
      options.setCreateIfMissing(true);

      List<ColumnFamilyHandle> cfHandles = new ArrayList<>();
      List<ColumnFamilyDescriptor> cfDescriptors = new ArrayList<>();
      cfDescriptors.add(new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY));
      cfDescriptors.add(
          new ColumnFamilyDescriptor(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE.getId()));
      try (final RocksDB rocksdb = RocksDB.openReadOnly(dbPath, cfDescriptors, cfHandles)) {
        for (ColumnFamilyHandle cfHandle : cfHandles) {
          RocksDbUsageHelper.printUsageForColumnFamily(rocksdb, cfHandle, out);
        }
      } catch (RocksDBException e) {
        LOG.error("TODO SLD ", e);
        e.printStackTrace();
        throw new RuntimeException(e);
      } finally {
        for (ColumnFamilyHandle cfHandle : cfHandles) {
          cfHandle.close();
        }
      }
    }

    private void recordResult(
        final boolean success, final AtomicInteger prunedCount, final Hash hash) {
      if (success) {
        prunedCount.getAndIncrement();
        LOG.atInfo().setMessage("Pruned {}").addArgument(hash::toHexString).log();
      } else {
        LOG.atInfo().setMessage("Failed to prune {}").addArgument(hash::toHexString).log();
      }
    }
  }
}
