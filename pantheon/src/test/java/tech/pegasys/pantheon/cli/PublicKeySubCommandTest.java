/*
 * Copyright 2018 ConsenSys AG.
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
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;

import java.io.File;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class PublicKeySubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_PUBLIC_KEY_USAGE =
      "Usage: pantheon public-key [-hV] [COMMAND]"
          + System.lineSeparator()
          + "This command provides node public key related actions."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  export  This command exports the node public key to a file."
          + System.lineSeparator();

  private static final String EXPECTED_PUBLIC_KEY_EXPORT_USAGE =
      "Usage: pantheon public-key export [-hV] --to=<FILE>"
          + System.lineSeparator()
          + "This command exports the node public key to a file."
          + System.lineSeparator()
          + "      --to=<FILE>   File to write public key to"
          + System.lineSeparator()
          + "  -h, --help        Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version     Print version information and exit."
          + System.lineSeparator();

  private static final String PUBLIC_KEY_SUBCOMMAND_NAME = "public-key";
  private static final String PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME = "export";

  // bublic-key sub-command
  @Test
  public void publicKeySubCommandExistAnbHaveSubCommands() {
    CommandSpec spec = parseCommand();
    assertThat(spec.subcommands()).containsKeys(PUBLIC_KEY_SUBCOMMAND_NAME);
    assertThat(spec.subcommands().get(PUBLIC_KEY_SUBCOMMAND_NAME).getSubcommands())
        .containsKeys(PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeySubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString()).startsWith(EXPECTED_PUBLIC_KEY_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeySubCommandHelpMustDisplayUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_PUBLIC_KEY_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  // Export sub-sub-command
  @Test
  public void callingPublicKeyExportSubCommandWithoutPathMustDisplayErrorAndUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME);
    final String expectedErrorOutputStart = "Missing required option '--to=<FILE>'";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingPublicKeyExportSubCommandHelpMustDisplayUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_PUBLIC_KEY_EXPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeyExportSubCommandWithFilePathMustWritePublicKeyInThisFile()
      throws Exception {

    final KeyPair keyPair = KeyPair.generate();

    when(mockController.getLocalNodeKeyPair()).thenReturn(keyPair);

    final File file = File.createTempFile("public", "key");

    parseCommand(
        PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME, "--to", file.getPath());

    assertThat(contentOf(file))
        .startsWith(keyPair.getPublicKey().toString())
        .endsWith(keyPair.getPublicKey().toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
