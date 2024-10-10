/*
 * Copyright ConsenSys AG.
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

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.api.query.StateBackupService;
import org.hyperledger.besu.ethereum.api.query.StateBackupService.BackupStatus;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.trie.forest.ForestWorldStateArchive;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import jakarta.validation.constraints.NotBlank;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** Subcommand that performs back up of state and accounts at a specified block */
@Command(
    name = "x-backup-state",
    description = "Backs up the state and accounts at a specified block.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class BackupState implements Runnable {

  /** Default constructor. */
  public BackupState() {}

  @Option(
      names = "--block",
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "The block to perform the backup at (default: calculated chain head)",
      arity = "1..1")
  private final Long block = Long.MAX_VALUE;

  @NotBlank
  @Option(
      names = "--backup-path",
      required = true,
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "The path to store the backup files.",
      arity = "1..1")
  private final File backupDir = null;

  @Option(
      names = {"--compression-enabled"},
      description = "Enable data compression",
      arity = "1")
  private final Boolean compress = true;

  @ParentCommand private OperatorSubCommand parentCommand;

  @Override
  public void run() {
    checkArgument(
        parentCommand.parentCommand.dataDir().toFile().exists(),
        "DataDir (the blockchain being backed up) does not exist.");
    checkArgument(
        backupDir.exists() || backupDir.mkdirs(),
        "Backup directory does not exist and cannot be created.");

    final BesuController besuController = createBesuController();
    final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();
    final ForestWorldStateKeyValueStorage forestWorldStateKeyValueStorage =
        ((ForestWorldStateArchive) besuController.getProtocolContext().getWorldStateArchive())
            .getWorldStateStorage();
    final EthScheduler scheduler = new EthScheduler(1, 1, 1, 1, new NoOpMetricsSystem());
    try {
      final long targetBlock = Math.min(blockchain.getChainHeadBlockNumber(), this.block);
      final StateBackupService backup =
          new StateBackupService(
              BesuInfo.version(),
              blockchain,
              backupDir.toPath(),
              scheduler,
              forestWorldStateKeyValueStorage);
      final BackupStatus status = backup.requestBackup(targetBlock, compress, Optional.empty());

      final double refValue = Math.pow(2, 256) / 100.0d;
      while (status.isBackingUp()) {
        if (status.getTargetBlockNum() != status.getStoredBlockNum()) {
          System.out.printf(
              "Chain Progress - %,d of %,d (%5.2f%%)%n",
              status.getStoredBlockNum(),
              status.getTargetBlockNum(),
              status.getStoredBlockNum() * 100.0d / status.getTargetBlockNum());
        } else {
          System.out.printf(
              "State Progress - %6.3f%% / %,d Accounts / %,d Storage Nodes%n",
              status.getCurrentAccountBytes().toUnsignedBigInteger().doubleValue() / refValue,
              status.getAccountCount(),
              status.getStorageCount());
        }
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(10));
      }

      System.out.printf(
          "Backup complete%n Accounts: %,d%n Code Size: %,d%nState Entries: %,d%n",
          status.getAccountCount(), status.getCodeSize(), status.getStorageCount());
    } finally {
      scheduler.stop();
      try {
        scheduler.awaitStop();
      } catch (final InterruptedException e) {
        // ignore
      }
    }
  }

  private BesuController createBesuController() {
    return parentCommand.parentCommand.buildController();
  }
}
