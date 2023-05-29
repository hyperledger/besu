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
package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.PasswordSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;

import java.io.PrintWriter;

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
    subcommands = {StorageSubCommand.RevertVariablesStorage.class})
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

  /** The Hash sub command for password. */
  @Command(
      name = "revert-variables",
      description = "This command revert the modifications done by the variables storage feature.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class RevertVariablesStorage implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private StorageSubCommand parentCommand;

    @Override
    public void run() {
      checkNotNull(parentCommand);

      final var controller = createBesuController();
      final var variablesStorage = controller.getStorageProvider().createVariablesStorage();
      variablesStorage.revert(controller.getProtocolSchedule(), controller.getStorageProvider());
    }

    private BesuController createBesuController() {
      return parentCommand.parentCommand.buildController();
    }
  }
}
