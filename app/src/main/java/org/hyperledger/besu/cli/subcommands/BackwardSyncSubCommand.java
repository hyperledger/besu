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
package org.hyperledger.besu.cli.subcommands;

import static org.hyperledger.besu.cli.subcommands.BackwardSyncSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.eth.sync.backwardsync.BackwardChain;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;

import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

/**
 * This class represents the BackwardSyncSubCommand. It is responsible for backward sync commands
 * Test.
 */
@CommandLine.Command(
    name = COMMAND_NAME,
    description = "Backward sync stuff.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class BackwardSyncSubCommand implements Runnable {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncSubCommand.class);

  /**
   * The command name for the BackwardSyncSubCommand. This constant is used as the name attribute in
   * the {@code CommandLine.Command} annotation.
   */
  public static final String COMMAND_NAME = "backward-sync";

  @CommandLine.Option(
      names = "--delete-cache",
      arity = "0..1",
      description = "delete the backward sync cache")
  private final Boolean deleteCacheEnabled = false;

  @SuppressWarnings("unused")
  @CommandLine.ParentCommand
  private BesuCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @CommandLine.Spec
  private CommandLine.Model.CommandSpec spec;

  private final PrintWriter out;

  /**
   * Instantiates a new BackwardSync sub command.
   *
   * @param out The PrintWriter where the usage will be reported.
   */
  public BackwardSyncSubCommand(final PrintWriter out) {
    this.out = out;
  }

  @Override
  public void run() {
    if (deleteCacheEnabled) {
      LOG.info("BWS subcommand deleting BWS cache");

      try (BesuController besuController = parentCommand.buildController()) {

        BackwardChain backwardChain =
            BackwardChain.from(
                besuController.getStorageProvider(),
                ScheduleBasedBlockHeaderFunctions.create(besuController.getProtocolSchedule()));
        backwardChain.clear();
      }

    } else {
      spec.commandLine().usage(out);
      LOG.info("set --delete-cache to true if you want to delete BWS cache");
    }
    LOG.info("BWS subcommand complete");
  }
}
