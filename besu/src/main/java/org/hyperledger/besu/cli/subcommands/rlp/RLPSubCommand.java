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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.subcommands.rlp.RLPSubCommand.EncodeSubCommand;
import org.hyperledger.besu.cli.util.VersionProvider;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

@Command(
    name = RLPSubCommand.COMMAND_NAME,
    description = "This command provides RLP data related actions.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {EncodeSubCommand.class})
public class RLPSubCommand implements Runnable {

  public static final String COMMAND_NAME = "rlp";

  private final PrintStream out;
  private final InputStream in;

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  public RLPSubCommand(final PrintStream out, final InputStream in) {
    this.out = out;
    this.in = in;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /**
   * RLP encode sub-command
   *
   * <p>Encode a JSON data into an RLP hex string.
   */
  @Command(
      name = "encode",
      description = "This command encodes a JSON typed data into an RLP hex string.",
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class EncodeSubCommand implements Runnable {

    @SuppressWarnings("unused")
    @ParentCommand
    private RLPSubCommand parentCommand; // Picocli injects reference to parent command

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Option(
        names = "--type",
        description =
            "Type of the RLP data to encode, possible values are ${COMPLETION-CANDIDATES}. (default: ${DEFAULT-VALUE})",
        arity = "1..1")
    private final RLPType type = RLPType.IBFT_EXTRA_DATA;

    @Option(
        names = "--from",
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = "File containing JSON object to encode",
        arity = "1..1")
    private final File jsonSourceFile = null;

    @Option(
        names = "--to",
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = "File to write encoded RLP string to.",
        arity = "1..1")
    private final File rlpTargetFile = null;

    @Override
    public void run() {
      checkNotNull(parentCommand);
      readInput();
    }

    /**
     * Reads the stdin or from a file if one is specified by {@link #jsonSourceFile} then goes to
     * {@link #encode(String)} this data
     */
    private void readInput() {
      // if we have an output file defined, print to it
      // otherwise print to defined output, usually standard output.
      StringBuilder jsonData = new StringBuilder();

      if (jsonSourceFile != null) {
        try {
          BufferedReader reader = Files.newBufferedReader(jsonSourceFile.toPath(), UTF_8);

          String line;
          while ((line = reader.readLine()) != null) jsonData.append(line);
        } catch (IOException e) {
          throw new ExecutionException(spec.commandLine(), "Unable to read JSON file.");
        }
      } else {
        // get JSON data from standard input
        try (Scanner scanner = new Scanner(parentCommand.in, UTF_8.name())) {
          while (scanner.hasNextLine()) {
            jsonData.append(String.join("", scanner.nextLine().split("\\s")));
          }
        }
      }

      // next step is to encode the value
      encode(jsonData.toString());
    }

    /**
     * Encodes the JSON input into an RLP data based on the {@link #type} then goes to {@link
     * #writeOutput(Bytes)} this data to file or stdout
     *
     * @param jsonInput the JSON string data to encode
     */
    private void encode(final String jsonInput) {
      if (jsonInput == null || jsonInput.isEmpty()) {
        throw new ParameterException(
            spec.commandLine(), "An error occurred while trying to read the JSON data.");
      } else {
        try {
          // encode and write the value
          writeOutput(type.getAdapter().encode(jsonInput));
        } catch (MismatchedInputException e) {
          throw new ParameterException(
              spec.commandLine(),
              "Unable to map the JSON data with selected type. Please check JSON input format. "
                  + e);
        } catch (IOException e) {
          throw new ParameterException(
              spec.commandLine(),
              "Unable to load the JSON data. Please check JSON input format. " + e);
        }
      }
    }

    /**
     * write the encoded result to stdout or a file if the option is specified
     *
     * @param rlpEncodedOutput the RLP output to write to file or stdout
     */
    private void writeOutput(final Bytes rlpEncodedOutput) {
      if (rlpTargetFile != null) {
        final Path targetPath = rlpTargetFile.toPath();

        try (final BufferedWriter fileWriter = Files.newBufferedWriter(targetPath, UTF_8)) {
          fileWriter.write(rlpEncodedOutput.toString());
        } catch (final IOException e) {
          throw new ParameterException(
              spec.commandLine(),
              "An error occurred while trying to write the RLP string. " + e.getMessage());
        }
      } else {
        parentCommand.out.println(rlpEncodedOutput);
      }
    }
  }
}
