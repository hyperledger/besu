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
package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Util;

import java.io.File;

import org.junit.Test;
import picocli.CommandLine.Model.CommandSpec;

public class PublicKeySubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_PUBLIC_KEY_USAGE =
      "Usage: besu public-key [-hV] [COMMAND]"
          + System.lineSeparator()
          + "This command provides node public key related actions."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  export          This command outputs the node public key. Default output is"
          + System.lineSeparator()
          + "                    standard output."
          + System.lineSeparator()
          + "  export-address  This command outputs the node's public key address. Default"
          + System.lineSeparator()
          + "                    output is standard output."
          + System.lineSeparator();

  private static final String EXPECTED_PUBLIC_KEY_EXPORT_USAGE =
      "Usage: besu public-key export [-hV] [--to=<FILE>]"
          + System.lineSeparator()
          + "This command outputs the node public key. Default output is standard output."
          + System.lineSeparator()
          + "      --to=<FILE>   File to write public key to instead of standard output"
          + System.lineSeparator()
          + "  -h, --help        Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version     Print version information and exit."
          + System.lineSeparator();

  private static final String EXPECTED_PUBLIC_KEY_EXPORT_ADDRESS_USAGE =
      "Usage: besu public-key export-address [-hV] [--to=<FILE>]"
          + System.lineSeparator()
          + "This command outputs the node's public key address. Default output is standard"
          + System.lineSeparator()
          + "output."
          + System.lineSeparator()
          + "      --to=<FILE>   File to write address to instead of standard output"
          + System.lineSeparator()
          + "  -h, --help        Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version     Print version information and exit."
          + System.lineSeparator();

  private static final String PUBLIC_KEY_SUBCOMMAND_NAME = "public-key";
  private static final String PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME = "export";
  private static final String PUBLIC_KEY_EXPORT_ADDRESS_SUBCOMMAND_NAME = "export-address";

  // public-key sub-command
  @Test
  public void publicKeySubCommandExistAnbHaveSubCommands() {
    CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(PUBLIC_KEY_SUBCOMMAND_NAME);
    assertThat(spec.subcommands().get(PUBLIC_KEY_SUBCOMMAND_NAME).getSubcommands())
        .containsKeys(PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME)
        .containsKeys(PUBLIC_KEY_EXPORT_ADDRESS_SUBCOMMAND_NAME);
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

  // Export public key sub-sub-command
  @Test
  public void callingPublicKeyExportSubCommandHelpMustDisplayUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_PUBLIC_KEY_EXPORT_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeyExportSubCommandWithoutPathMustWriteKeyToStandardOutput() {
    final KeyPair keyPair = KeyPair.generate();

    parseCommand(f -> keyPair, PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME);

    final String expectedOutputStart = keyPair.getPublicKey().toString();
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeyExportSubCommandWithFilePathMustWritePublicKeyInThisFile()
      throws Exception {

    final KeyPair keyPair = KeyPair.generate();

    final File file = File.createTempFile("public", "key");

    parseCommand(
        f -> keyPair,
        PUBLIC_KEY_SUBCOMMAND_NAME,
        PUBLIC_KEY_EXPORT_SUBCOMMAND_NAME,
        "--to",
        file.getPath());

    assertThat(contentOf(file))
        .startsWith(keyPair.getPublicKey().toString())
        .endsWith(keyPair.getPublicKey().toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  // Export address sub-sub-command
  @Test
  public void callingPublicKeyExportAddressSubCommandHelpMustDisplayUsage() {
    parseCommand(PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_ADDRESS_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString()).startsWith(EXPECTED_PUBLIC_KEY_EXPORT_ADDRESS_USAGE);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeyExportAddressSubCommandWithoutPathMustWriteAddressToStandardOutput() {
    final KeyPair keyPair = KeyPair.generate();

    parseCommand(
        f -> keyPair, PUBLIC_KEY_SUBCOMMAND_NAME, PUBLIC_KEY_EXPORT_ADDRESS_SUBCOMMAND_NAME);

    final String expectedOutputStart = Util.publicKeyToAddress(keyPair.getPublicKey()).toString();
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingPublicKeyExportAddressSubCommandWithFilePathMustWriteAddressInThisFile()
      throws Exception {

    final KeyPair keyPair = KeyPair.generate();

    final File file = File.createTempFile("public", "address");

    parseCommand(
        f -> keyPair,
        PUBLIC_KEY_SUBCOMMAND_NAME,
        PUBLIC_KEY_EXPORT_ADDRESS_SUBCOMMAND_NAME,
        "--to",
        file.getPath());

    assertThat(contentOf(file))
        .startsWith(Util.publicKeyToAddress(keyPair.getPublicKey()).toString())
        .endsWith(Util.publicKeyToAddress(keyPair.getPublicKey()).toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
