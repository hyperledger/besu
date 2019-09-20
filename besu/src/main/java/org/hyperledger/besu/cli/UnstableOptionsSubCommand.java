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
package org.hyperledger.besu.cli;

import static org.hyperledger.besu.cli.UnstableOptionsSubCommand.COMMAND_NAME;

import java.io.PrintStream;
import java.util.Map;

import picocli.CommandLine;

/**
 * This class provides a CLI command to enumerate the unstable options available in Besu.
 *
 * <p>Unstable options are distinguished by
 *
 * <ul>
 *   <li>Not being configured in the main BesuCommand
 *   <li>Being marked as 'hidden'
 *   <li>Having their first option name start with <code>--X</code>
 * </ul>
 *
 * There is no stability or compatibility guarantee for options marked as unstable between releases.
 * They can be added and removed without announcement and their meaning and values can similarly
 * change without announcement or warning.
 */
@CommandLine.Command(
    name = COMMAND_NAME,
    aliases = {"-X", "--Xhelp"},
    description = "This command provides help text for all unstable options.",
    hidden = true,
    helpCommand = true)
public class UnstableOptionsSubCommand implements Runnable, CommandLine.IHelpCommandInitializable {

  static final String COMMAND_NAME = "Xhelp";

  private final CommandLine commandLine;
  private CommandLine.Help.Ansi ansi;
  private PrintStream out;

  private UnstableOptionsSubCommand(final CommandLine commandLine) {
    this.commandLine = commandLine;
  }

  @Override
  public void run() {
    commandLine.getCommandSpec().mixins().forEach(this::printUnstableOptions);
  }

  @Override
  public void init(
      final CommandLine helpCommandLine,
      final CommandLine.Help.Ansi ansi,
      final PrintStream out,
      final PrintStream err) {
    this.ansi = ansi;
    this.out = out;
  }

  static void createUnstableOptions(
      final CommandLine commandLine, final Map<String, Object> mixins) {
    final UnstableOptionsSubCommand listOptionsCommand = new UnstableOptionsSubCommand(commandLine);
    mixins.forEach(commandLine::addMixin);
    commandLine.addSubcommand(COMMAND_NAME, listOptionsCommand);
  }

  private void printUnstableOptions(
      final String mixinName, final CommandLine.Model.CommandSpec commandSpec) {
    // Recreate the options but flip hidden to false.
    final CommandLine.Model.CommandSpec cs = CommandLine.Model.CommandSpec.create();
    commandSpec.options().stream()
        .filter(option -> option.hidden() && option.names()[0].startsWith("--X"))
        .forEach(option -> cs.addOption(option.toBuilder().hidden(false).build()));

    if (cs.options().size() > 0) {
      // Print out the help text.
      out.printf("Unstable options for %s%n", mixinName);
      out.println(new CommandLine.Help(cs, ansi).optionList());
    }
  }
}
