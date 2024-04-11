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
package org.hyperledger.besu.cli.subcommands.rlp;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.CommandTestAbstract;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.Model.CommandSpec;

public class RLPSubCommandTest extends CommandTestAbstract {

  private static final String EXPECTED_RLP_USAGE =
      "Usage: besu rlp [-hV] [COMMAND]"
          + System.lineSeparator()
          + "This command provides RLP data related actions."
          + System.lineSeparator()
          + "  -h, --help      Show this help message and exit."
          + System.lineSeparator()
          + "  -V, --version   Print version information and exit."
          + System.lineSeparator()
          + "Commands:"
          + System.lineSeparator()
          + "  encode  This command encodes a JSON typed data into an RLP hex string.";

  private static final String EXPECTED_RLP_ENCODE_USAGE =
      "Usage: besu rlp encode [-hV] [--from=<FILE>] [--to=<FILE>] [--type=<type>]"
          + System.lineSeparator()
          + "This command encodes a JSON typed data into an RLP hex string."
          + System.lineSeparator()
          + "      --from=<FILE>   File containing JSON object to encode"
          + System.lineSeparator()
          + "  -h, --help          Show this help message and exit."
          + System.lineSeparator()
          + "      --to=<FILE>     File to write encoded RLP string to."
          + System.lineSeparator()
          + "      --type=<type>   Type of the RLP data to encode, possible values are"
          + System.lineSeparator()
          + "                        IBFT_EXTRA_DATA, QBFT_EXTRA_DATA. (default:"
          + System.lineSeparator()
          + "                        IBFT_EXTRA_DATA)"
          + System.lineSeparator()
          + "  -V, --version       Print version information and exit.";

  private static final String RLP_SUBCOMMAND_NAME = "rlp";
  private static final String RLP_ENCODE_SUBCOMMAND_NAME = "encode";
  private static final String RLP_DECODE_SUBCOMMAND_NAME = "decode";
  private static final String RLP_QBFT_TYPE = "QBFT_EXTRA_DATA";

  // RLP sub-command
  @Test
  public void rlpSubCommandExistsAndHasSubCommands() {
    final CommandSpec spec = parseCommand().getSpec();
    assertThat(spec.subcommands()).containsKeys(RLP_SUBCOMMAND_NAME);
    assertThat(spec.subcommands().get(RLP_SUBCOMMAND_NAME).getSubcommands())
        .containsKeys(RLP_ENCODE_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingRLPSubCommandWithoutSubSubcommandMustDisplayUsage() {
    parseCommand(RLP_SUBCOMMAND_NAME);
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_RLP_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingRPLSubCommandHelpMustDisplayUsage() {
    parseCommand(RLP_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_RLP_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  // Encode RLP sub-command
  @Test
  public void callingRPLEncodeSubCommandHelpMustDisplayUsage() {
    parseCommand(RLP_SUBCOMMAND_NAME, RLP_ENCODE_SUBCOMMAND_NAME, "--help");
    assertThat(commandOutput.toString(UTF_8)).startsWith(EXPECTED_RLP_ENCODE_USAGE);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingRPLSubCommandVersionMustDisplayVersion() {
    parseCommand(RLP_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void callingRPLEncodeSubCommandVersionMustDisplayVersion() {
    parseCommand(RLP_SUBCOMMAND_NAME, RLP_ENCODE_SUBCOMMAND_NAME, "--version");
    assertThat(commandOutput.toString(UTF_8)).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void encodeWithoutPathMustWriteToStandardOutput() {

    final String jsonInput =
        "[\"be068f726a13c8d46c44be6ce9d275600e1735a4\", \"5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193\"]";

    // set stdin
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(jsonInput.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_ENCODE_SUBCOMMAND_NAME);

    final String expectedRlpString =
        "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
            + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0";
    assertThat(commandOutput.toString(UTF_8)).contains(expectedRlpString);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void encodeWithOutputFileMustWriteInThisFile() throws Exception {

    final File file = File.createTempFile("ibftExtraData", "rlp");

    final String jsonInput =
        "[\"be068f726a13c8d46c44be6ce9d275600e1735a4\", \"5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193\"]";

    // set stdin
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(jsonInput.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_ENCODE_SUBCOMMAND_NAME, "--to", file.getPath());

    final String expectedRlpString =
        "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
            + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0";

    assertThat(contentOf(file)).contains(expectedRlpString);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void encodeWithInputFilePathMustReadFromThisFile(final @TempDir Path dir)
      throws Exception {
    final Path tempJsonFile = Files.createTempFile(dir, "input", "json");
    try (final BufferedWriter fileWriter = Files.newBufferedWriter(tempJsonFile, UTF_8)) {

      fileWriter.write(
          "[\"be068f726a13c8d46c44be6ce9d275600e1735a4\", \"5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193\"]");

      fileWriter.flush();

      parseCommand(
          RLP_SUBCOMMAND_NAME,
          RLP_ENCODE_SUBCOMMAND_NAME,
          "--from",
          tempJsonFile.toFile().getAbsolutePath());

      final String expectedRlpString =
          "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
              + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0";
      assertThat(commandOutput.toString(UTF_8)).contains(expectedRlpString);
      assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    }
  }

  @Test
  public void canEncodeToQbftExtraData(final @TempDir Path dir) throws Exception {
    final Path tempJsonFile = Files.createTempFile(dir, "qbft", "json");
    try (final BufferedWriter fileWriter = Files.newBufferedWriter(tempJsonFile, UTF_8)) {

      fileWriter.write(
          "[\"be068f726a13c8d46c44be6ce9d275600e1735a4\", \"5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193\"]");

      fileWriter.flush();

      parseCommand(
          RLP_SUBCOMMAND_NAME,
          RLP_ENCODE_SUBCOMMAND_NAME,
          "--from",
          tempJsonFile.toFile().getAbsolutePath(),
          "--type",
          RLP_QBFT_TYPE);

      final String expectedRlpString =
          "0xf84fa00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
              + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193c080c0";
      assertThat(commandOutput.toString(UTF_8)).contains(expectedRlpString);
      assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    }
  }

  @Test
  public void encodeWithInvalidInputMustRaiseAnError(final @TempDir Path dir) throws Exception {
    final Path tempJsonFile = Files.createTempFile(dir, "invalid", "json");
    try (final BufferedWriter fileWriter = Files.newBufferedWriter(tempJsonFile, UTF_8)) {

      fileWriter.write("{\"property\":0}");

      fileWriter.flush();

      parseCommand(
          RLP_SUBCOMMAND_NAME,
          RLP_ENCODE_SUBCOMMAND_NAME,
          "--from",
          tempJsonFile.toFile().getAbsolutePath());

      assertThat(commandOutput.toString(UTF_8)).isEmpty();
      assertThat(commandErrorOutput.toString(UTF_8))
          .startsWith(
              "Unable to map the JSON data with selected type. Please check JSON input format.");
    }
  }

  @Test
  public void encodeWithEmptyInputMustRaiseAnError(final @TempDir Path dir) throws Exception {
    final Path emptyFile = Files.createTempFile(dir, "empty", "json");
    parseCommand(
        RLP_SUBCOMMAND_NAME,
        RLP_ENCODE_SUBCOMMAND_NAME,
        "--from",
        emptyFile.toFile().getAbsolutePath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("An error occurred while trying to read the JSON data.");
  }

  @Test
  public void encodeWithEmptyStdInputMustRaiseAnError() throws Exception {

    // set empty stdin
    final String jsonInput = "";
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(jsonInput.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_ENCODE_SUBCOMMAND_NAME);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("An error occurred while trying to read the JSON data.");
  }

  @Test
  public void decodeWithoutPathMustWriteToStandardOutput() {

    final String inputData =
        "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
            + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0";

    // set stdin
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(inputData.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_DECODE_SUBCOMMAND_NAME);

    final String expectedValidatorString =
        "[0xbe068f726a13c8d46c44be6ce9d275600e1735a4, 0x5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193]";
    assertThat(commandOutput.toString(UTF_8)).contains(expectedValidatorString);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void decodeQBFTWithoutPathMustWriteToStandardOutput() {

    final String inputData =
        "0xf84fa00000000000000000000000000000000000000000000000000000000000000000ea94241f804efb46f71acaa"
            + "5be94a62f7798e89c3724946cdf72da457453063ea92e7fa5ac30afbcec28cdc080c0";

    // set stdin
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(inputData.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_DECODE_SUBCOMMAND_NAME, "--type", RLP_QBFT_TYPE);

    final String expectedValidatorString =
        "[0x241f804efb46f71acaa5be94a62f7798e89c3724, 0x6cdf72da457453063ea92e7fa5ac30afbcec28cd]";
    assertThat(commandOutput.toString(UTF_8)).contains(expectedValidatorString);
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void decodeWithOutputFileMustWriteInThisFile() throws Exception {

    final File file = File.createTempFile("ibftValidators", "rlp");

    final String inputData =
        "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d"
            + "46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0";

    // set stdin
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(inputData.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_DECODE_SUBCOMMAND_NAME, "--to", file.getPath());

    final String expectedValidatorString =
        "[0xbe068f726a13c8d46c44be6ce9d275600e1735a4, 0x5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193]";

    assertThat(contentOf(file)).contains(expectedValidatorString);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
  }

  @Test
  public void decodeWithInputFilePathMustReadFromThisFile(final @TempDir Path dir)
      throws Exception {
    final Path tempJsonFile = Files.createTempFile(dir, "input", "json");
    try (final BufferedWriter fileWriter = Files.newBufferedWriter(tempJsonFile, UTF_8)) {

      fileWriter.write(
          "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0");

      fileWriter.flush();

      parseCommand(
          RLP_SUBCOMMAND_NAME,
          RLP_DECODE_SUBCOMMAND_NAME,
          "--from",
          tempJsonFile.toFile().getAbsolutePath());

      final String expectedValidatorString =
          "[0xbe068f726a13c8d46c44be6ce9d275600e1735a4, 0x5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193]";

      assertThat(commandOutput.toString(UTF_8)).contains(expectedValidatorString);
      assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    }
  }

  @Test
  public void decodeWithInputFilePathToOutputFile(final @TempDir Path dir) throws Exception {
    final Path tempInputFile = Files.createTempFile(dir, "input", "json");
    final File tempOutputFile = File.createTempFile("ibftValidators", "rlp");
    try (final BufferedWriter fileWriter = Files.newBufferedWriter(tempInputFile, UTF_8)) {

      fileWriter.write(
          "0xf853a00000000000000000000000000000000000000000000000000000000000000000ea94be068f726a13c8d46c44be6ce9d275600e1735a4945ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193808400000000c0");

      fileWriter.flush();

      parseCommand(
          RLP_SUBCOMMAND_NAME,
          RLP_DECODE_SUBCOMMAND_NAME,
          "--from",
          tempInputFile.toFile().getAbsolutePath(),
          "--to",
          tempOutputFile.getPath());

      final String expectedValidatorString =
          "[0xbe068f726a13c8d46c44be6ce9d275600e1735a4, 0x5ff6f4b66a46a2b2310a6f3a93aaddc0d9a1c193]";

      assertThat(contentOf(tempOutputFile)).contains(expectedValidatorString);
      assertThat(commandOutput.toString(UTF_8)).isEmpty();
      assertThat(commandErrorOutput.toString(UTF_8)).isEmpty();
    }
  }

  @Test
  public void decodeWithEmptyStdInputMustRaiseAnError() throws Exception {

    // set empty stdin
    final String jsonInput = "";
    final ByteArrayInputStream stdIn = new ByteArrayInputStream(jsonInput.getBytes(UTF_8));

    parseCommand(stdIn, RLP_SUBCOMMAND_NAME, RLP_DECODE_SUBCOMMAND_NAME);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).startsWith("Unable to read input data.");
  }

  @Test
  public void decodeWithInputFilePathMustThrowErrorFileNotExist(final @TempDir Path dir)
      throws Exception {

    final String nonExistingFileName = "/incorrectPath/wrongFile.json";

    parseCommand(RLP_SUBCOMMAND_NAME, RLP_DECODE_SUBCOMMAND_NAME, "--from", nonExistingFileName);

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8)).contains("Unable to read input file");
  }

  @Test
  public void decodeWithEmptyInputMustRaiseAnError(final @TempDir Path dir) throws Exception {
    final Path emptyFile = Files.createTempFile(dir, "empty", "json");
    parseCommand(
        RLP_SUBCOMMAND_NAME,
        RLP_DECODE_SUBCOMMAND_NAME,
        "--from",
        emptyFile.toFile().getAbsolutePath());

    assertThat(commandOutput.toString(UTF_8)).isEmpty();
    assertThat(commandErrorOutput.toString(UTF_8))
        .startsWith("An error occurred while trying to read the input data.");
  }

  @AfterEach
  public void restoreStdin() {
    System.setIn(System.in);
  }
}
