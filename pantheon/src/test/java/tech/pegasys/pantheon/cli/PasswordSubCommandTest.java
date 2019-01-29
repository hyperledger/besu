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
package tech.pegasys.pantheon.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class PasswordSubCommandTest extends CommandTestAbstract {

  @Test
  public void passwordHashSubCommandExists() {
    CommandSpec spec = parseCommand();
    assertThat(spec.subcommands()).containsKeys("password-hash");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPasswordHashWithoutPasswordParameterMustDisplayUsage() {
    final String expectedUsage =
        "Missing required parameter: <password>"
            + System.lineSeparator()
            + "Usage: pantheon password-hash [-hV] <password>"
            + System.lineSeparator()
            + "This command generates the hash of a given password."
            + System.lineSeparator()
            + "      <password>   The password input"
            + System.lineSeparator()
            + "  -h, --help       Show this help message and exit."
            + System.lineSeparator()
            + "  -V, --version    Print version information and exit."
            + System.lineSeparator();

    parseCommand("password-hash");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith(expectedUsage);
  }

  @Test
  public void publicKeySubCommandExistAnbHaveSubCommands() {
    parseCommand("password-hash", "foo");

    // we can't predict the final value so we are only checking if it starts with the hash marker
    assertThat(commandOutput.toString()).startsWith("$2");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
