/*
 * Copyright contributors to Hyperledger Besu.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.util.TomlConfigurationDefaultProvider;
import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.PrintWriter;
import java.nio.file.Path;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/** The Besu configuration Validate sub command. */
@Command(
    name = COMMAND_NAME,
    description = "This command provides basic Besu config validation (syntax only).",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class ValidateConfigSubCommand implements Runnable {

  /** The constant COMMAND_NAME. */
  public static final String COMMAND_NAME = "validate-config";

  @Option(
      names = "--config-file",
      paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
      description = "Path to Besu config file")
  private final Path dataPath = DefaultCommandValues.getDefaultBesuDataPath(this);

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  private final PrintWriter out;
  private final CommandLine commandLine;

  /**
   * Instantiates a new Validate config sub command.
   *
   * @param commandLine the command line
   * @param out the PrintWriter where validation results will be reported.
   */
  public ValidateConfigSubCommand(final CommandLine commandLine, final PrintWriter out) {
    this.out = out;
    this.commandLine = commandLine;
  }

  @Override
  public void run() {
    checkNotNull(parentCommand);
    try {
      TomlConfigurationDefaultProvider.fromFile(commandLine, dataPath.toFile())
          .loadConfigurationIfNotLoaded();
    } catch (Exception e) {
      this.out.println(e);
      return;
    }
    this.out.println(
        "TOML config file is valid on basic inspection. Further dependencies between related options are checked when Besu starts.");
  }
}
