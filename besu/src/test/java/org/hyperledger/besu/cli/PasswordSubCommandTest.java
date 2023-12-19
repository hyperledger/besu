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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.BesuInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine.Model.CommandSpec;

@ExtendWith(MockitoExtension.class)
public class PasswordSubCommandTest extends CommandTestAbstract {

  @Test
  public void passwordSubCommandExistsWithHashSubCommand() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys("password");
    assertThat(spec.subcommands().get("password").getSubcommands()).containsKeys("hash");
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void passwordSubCommandExists() {
    parseCommand("password");

    assertThat(commandOutput.toString(UTF_8))
        .contains("This command provides password related actions");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void passwordHashSubCommandExists() {
    parseCommand("password", "hash");

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .contains("Missing required option: '--password=<password>'");
  }

  @Test
  public void passwordHashSubCommandHelpDisplaysHelp() {
    parseCommand("password", "hash", "--help");

    assertThat(commandOutput.toString(UTF_8))
        .contains("Usage: besu password hash [-hV] --password=<password>");
    assertThat(commandOutput.toString(UTF_8))
        .contains("This command generates the hash of a given password");
  }

  @Test
  public void passwordSubCommandVersionDisplaysVersion() {
    parseCommand("password", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void passwordHashSubCommandVersionDisplaysVersion() {
    parseCommand("password", "hash", "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void passwordHashSubCommandHashesPassword() {
    parseCommand("password", "hash", "--password", "foo");

    // we can't predict the final value so we are only checking if it starts with the hash marker
    assertThat(commandOutput.toString(UTF_8)).startsWith("$2");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }
}
