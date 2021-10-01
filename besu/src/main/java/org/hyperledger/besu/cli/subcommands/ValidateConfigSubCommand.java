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
package org.hyperledger.besu.cli.subcommands;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.ethereum.permissioning.TomlConfigFileParser;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.PrintStream;
import java.nio.file.Path;

import static org.hyperledger.besu.cli.subcommands.ValidateConfigSubCommand.COMMAND_NAME;

@Command(
    name = COMMAND_NAME,
    description = "This command provides config validation related actions.",
    mixinStandardHelpOptions = true)
public class ValidateConfigSubCommand implements Runnable {

  public static final String COMMAND_NAME = "validate-config";

  @Option(
          names = "--config-file",
          paramLabel = DefaultCommandValues.MANDATORY_PATH_FORMAT_HELP,
          description = "The path to Besu config file")
  private final Path dataPath = DefaultCommandValues.getDefaultBesuDataPath(this);

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  final PrintStream out;

  public ValidateConfigSubCommand(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    try {
      TomlConfigFileParser.loadConfigurationFromFile(dataPath.toString());
    } catch (Exception e) {
      this.out.print(e);
    }
    this.out.print("This toml config file is valid.");
  }
}
