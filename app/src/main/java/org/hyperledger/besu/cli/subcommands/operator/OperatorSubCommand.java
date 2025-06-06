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

import static org.hyperledger.besu.cli.subcommands.operator.OperatorSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.PrintWriter;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Operator related sub-command */
@Command(
    name = COMMAND_NAME,
    description = "Operator related actions such as generating configuration and caches.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {
      GenerateBlockchainConfig.class,
      GenerateLogBloomCache.class,
      BackupState.class,
      RestoreState.class
    })
public class OperatorSubCommand implements Runnable {

  /** The constant COMMAND_NAME. */
  public static final String COMMAND_NAME = "operator";

  /** The constant GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME. */
  public static final String GENERATE_BLOCKCHAIN_CONFIG_SUBCOMMAND_NAME =
      "generate-blockchain-config";

  /** The Parent command. */
  @SuppressWarnings("unused")
  @ParentCommand
  BesuCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  private final PrintWriter out;

  /**
   * Instantiates a new Operator sub command.
   *
   * @param out the out
   */
  public OperatorSubCommand(final PrintWriter out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }
}
