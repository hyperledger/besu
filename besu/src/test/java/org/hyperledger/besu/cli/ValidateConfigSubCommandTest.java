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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.BesuInfo;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.io.Resources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine.Model.CommandSpec;

@ExtendWith(MockitoExtension.class)
public class ValidateConfigSubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_PUBLIC_KEY_USAGE =
      "Usage: besu validate-config [-hV] [--config-file=<PATH>]"
          + System.lineSeparator()
          + "This command provides basic Besu config validation (syntax only)."
          + System.lineSeparator()
          + "      --config-file=<PATH>   Path to Besu config file"
          + System.lineSeparator()
          + "  -h, --help                 Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version              Print version information and exit."
          + System.lineSeparator();

  private static final String VALIDATE_CONFIG_SUBCOMMAND_NAME = "validate-config";

  @Test
  public void validateConfigSubCommandExists() {
    CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(VALIDATE_CONFIG_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingValidateConfigSubCommandHelpMustDisplayUsage() {
    parseCommand(VALIDATE_CONFIG_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_PUBLIC_KEY_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingValidateConfigSubCommandVersionMustDisplayVersion() {
    parseCommand(VALIDATE_CONFIG_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingValidateConfigSubCommandWithNonExistentMustDisplayError() {
    parseCommand(VALIDATE_CONFIG_SUBCOMMAND_NAME, "--config-file", "/non/existent/file");
    assertThat(commandOutput.toString(UTF_8))
        .contains("Unable to read TOML configuration, file not found.");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingValidateConfigSubCommandWithInvalidFileMustDisplayError() throws IOException {
    final Path invalidToml = Files.createTempFile("invalid", "toml");
    Files.writeString(invalidToml, "xyz=");
    invalidToml.toFile().deleteOnExit();

    parseCommand(VALIDATE_CONFIG_SUBCOMMAND_NAME, "--config-file", invalidToml.toString());
    assertThat(commandOutput.toString(UTF_8)).contains("Invalid TOML configuration");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingValidateConfigSubCommandWithValidTomlFileSucceeds() throws IOException {

    final URL configFile = this.getClass().getResource("/everything_config.toml");
    final Path validToml = Files.createTempFile("valid", "toml");
    Files.write(validToml, Resources.toByteArray(configFile));
    validToml.toFile().deleteOnExit();

    parseCommand(VALIDATE_CONFIG_SUBCOMMAND_NAME, "--config-file", validToml.toString());
    assertThat(commandOutput.toString(UTF_8)).startsWith("TOML config file is valid");
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }
}
