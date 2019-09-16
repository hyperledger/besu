/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class PasswordSubCommandTest extends CommandTestAbstract {

  @Test
  public void passwordSubCommandExistAnbHaveSubCommands() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys("password");
    assertThat(spec.subcommands().get("password").getSubcommands()).containsKeys("hash");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void passwordSubCommandExists() {
    parseCommand("password");

    assertThat(commandOutput.toString()).contains("This command provides password related actions");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void passwordHashSubCommandExist() {
    parseCommand("password", "hash");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Missing required option '--password=<password>'");
    assertThat(commandErrorOutput.toString())
        .contains("Usage: besu password hash [-hV] --password=<password>");
    assertThat(commandErrorOutput.toString())
        .contains("This command generates the hash of a given password");
  }

  @Test
  public void passwordHashSubCommandHashesPassword() {
    parseCommand("password", "hash", "--password", "foo");

    // we can't predict the final value so we are only checking if it starts with the hash marker
    assertThat(commandOutput.toString()).startsWith("$2");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
