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
package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.storage.StorageSubCommand.COMMAND_NAME;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.CHAIN_HEAD_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FINALIZED_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.FORK_HEADS;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SAFE_BLOCK_HASH;
import static org.hyperledger.besu.ethereum.chain.VariablesStorage.Keys.SEQ_NO_STORE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE_BY_HASH;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.CODE_STORAGE_COMPARE;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;

import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** The Storage sub command. */
@Command(
    name = COMMAND_NAME,
    description = "This command provides storage related actions.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      StorageSubCommand.RevertVariablesStorage.class,
      StorageSubCommand.MigrateCodeStorage.class
    })
public class StorageSubCommand implements Runnable {

  /** The constant COMMAND_NAME. */
  public static final String COMMAND_NAME = "storage";

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  private final PrintWriter out;

  /**
   * Instantiates a new Storage sub command.
   *
   * @param out The PrintWriter where the usage will be reported.
   */
  public StorageSubCommand(final PrintWriter out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /** The revert variables sub command for storage. */
  @Command(
      name = "revert-variables",
      description = "This command revert the modifications done by the variables storage feature.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class RevertVariablesStorage implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(RevertVariablesStorage.class);
    private static final Bytes VARIABLES_PREFIX = Bytes.of(1);

    @SuppressWarnings("unused")
    @ParentCommand
    private StorageSubCommand parentCommand;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final var storageProvider = getStorageProvider();

      revert(storageProvider);
    }

    private StorageProvider getStorageProvider() {
      // init collection of ignorable segments
      parentCommand.parentCommand.setIgnorableStorageSegments();
      return parentCommand.parentCommand.getStorageProvider();
    }

    private void revert(final StorageProvider storageProvider) {
      final var variablesStorage = storageProvider.createVariablesStorage();
      final var blockchainStorage =
          getStorageProvider().getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN);
      final var blockchainUpdater = blockchainStorage.startTransaction();
      final var variablesUpdater = variablesStorage.updater();

      variablesStorage
          .getChainHead()
          .ifPresent(
              v -> {
                setBlockchainVariable(
                    blockchainUpdater, VARIABLES_PREFIX, CHAIN_HEAD_HASH.getBytes(), v);
                LOG.info("Reverted variable storage for key {}", CHAIN_HEAD_HASH);
              });

      variablesStorage
          .getFinalized()
          .ifPresent(
              v -> {
                setBlockchainVariable(
                    blockchainUpdater, VARIABLES_PREFIX, FINALIZED_BLOCK_HASH.getBytes(), v);
                LOG.info("Reverted variable storage for key {}", FINALIZED_BLOCK_HASH);
              });

      variablesStorage
          .getSafeBlock()
          .ifPresent(
              v -> {
                setBlockchainVariable(
                    blockchainUpdater, VARIABLES_PREFIX, SAFE_BLOCK_HASH.getBytes(), v);
                LOG.info("Reverted variable storage for key {}", SAFE_BLOCK_HASH);
              });

      final var forkHeads = variablesStorage.getForkHeads();
      if (!forkHeads.isEmpty()) {
        setBlockchainVariable(
            blockchainUpdater,
            VARIABLES_PREFIX,
            FORK_HEADS.getBytes(),
            RLP.encode(o -> o.writeList(forkHeads, (val, out) -> out.writeBytes(val))));
        LOG.info("Reverted variable storage for key {}", FORK_HEADS);
      }

      variablesStorage
          .getLocalEnrSeqno()
          .ifPresent(
              v -> {
                setBlockchainVariable(blockchainUpdater, Bytes.EMPTY, SEQ_NO_STORE.getBytes(), v);
                LOG.info("Reverted variable storage for key {}", SEQ_NO_STORE);
              });

      variablesUpdater.removeAll();

      variablesUpdater.commit();
      blockchainUpdater.commit();
    }

    private void setBlockchainVariable(
        final KeyValueStorageTransaction blockchainTransaction,
        final Bytes prefix,
        final Bytes key,
        final Bytes value) {
      blockchainTransaction.put(
          Bytes.concatenate(prefix, key).toArrayUnsafe(), value.toArrayUnsafe());
    }
  }

  /** The revert variables sub command for storage. */
  @Command(
      name = "compare-code",
      description =
          "This command compare the code storage to be stored by code hash necessary for snap sync support.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class MigrateCodeStorage implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MigrateCodeStorage.class);

    @SuppressWarnings("unused")
    @ParentCommand
    private StorageSubCommand parentCommand;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final var storageProvider = getStorageProvider();

      migrate(storageProvider);
    }

    private StorageProvider getStorageProvider() {
      // init collection of ignorable segments
      parentCommand.parentCommand.setIgnorableStorageSegments();
      return parentCommand.parentCommand.getStorageProvider();
    }

    private void migrate(final StorageProvider storageProvider) {
      LOG.info("Starting code storage comparison");
      final Instant start = Instant.now();

      final SegmentedKeyValueStorage keyValueStorage =
          storageProvider.getStorageBySegmentIdentifiers(
              List.of(CODE_STORAGE, CODE_STORAGE_BY_HASH, CODE_STORAGE_COMPARE));

      keyValueStorage.stream(CODE_STORAGE)
          .forEach(
              keyValuePair -> {
                final Bytes value = Bytes.wrap(keyValuePair.getValue());
                final Bytes32 codeHash = Hash.hash(value);
                final Optional<byte[]> storageByHashValue =
                    keyValueStorage.get(CODE_STORAGE_BY_HASH, codeHash.toArray());
                if (storageByHashValue.isEmpty()) {
                  LOG.info(
                      "Missing code in CODE_STORAGE_BY_HASH for hash={} value={}", codeHash, value);
                }
                storageByHashValue.ifPresent(
                    v -> {
                      if (!Bytes.wrap(v).equals(value)) {
                        LOG.info(
                            "Code stored in CODE_STORAGE_BY_HASH has incorrect code value expected={} actual={}",
                            v,
                            value);
                      }
                    });

                // don't need to store the counts. this is just to flatten storage for comparison
                final SegmentedKeyValueStorageTransaction transaction =
                    keyValueStorage.startTransaction();
                transaction.put(
                    CODE_STORAGE_COMPARE, codeHash.toArrayUnsafe(), keyValuePair.getValue());
                transaction.commit();
              });

      keyValueStorage.stream(CODE_STORAGE_BY_HASH)
          .forEach(
              keyValuePair -> {
                final Optional<byte[]> codeStorageCompareValue =
                    keyValueStorage.get(CODE_STORAGE_COMPARE, keyValuePair.getKey());
                if (codeStorageCompareValue.isEmpty()) {
                  LOG.info(
                      "Missing code in CODE_STORAGE_COMPARE for hash={} value={}",
                      Bytes.wrap(keyValuePair.getKey()),
                      Bytes.wrap(keyValuePair.getValue()));
                }
                codeStorageCompareValue.ifPresent(
                    cv -> {
                      if (!Bytes.wrap(cv).equals(Bytes.wrap(keyValuePair.getValue()))) {
                        LOG.info(
                            "Code stored in CODE_STORAGE_BY_HASH has incorrect code value expected={} actual={}",
                            Bytes.wrap(keyValuePair.getKey()),
                            Bytes.wrap(cv));
                      }
                    });
              });

      LOG.info(
          "Finished code storage comparison in {}",
          DurationFormatUtils.formatDurationHMS(Duration.between(start, Instant.now()).toMillis()));
    }
  }
}
