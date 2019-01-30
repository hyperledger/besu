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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.PublicKeySubCommand.COMMAND_NAME;

import tech.pegasys.pantheon.cli.PublicKeySubCommand.ExportSubCommand;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Node's public key related sub-command */
@Command(
  name = COMMAND_NAME,
  description = "This command provides node public key related actions.",
  mixinStandardHelpOptions = true,
  subcommands = {ExportSubCommand.class}
)
class PublicKeySubCommand implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  static final String COMMAND_NAME = "public-key";

  @SuppressWarnings("unused")
  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  private final PrintStream out;

  PublicKeySubCommand(final PrintStream out) {
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /**
   * Public key export sub-command
   *
   * <p>Export of the public key takes a file as parameter to export directly by writing the key in
   * the file. A direct output of the key to standard out is not done because we don't want the key
   * value to be polluted by other information like logs that are in KeyPairUtil that is inevitable.
   */
  @Command(
    name = "export",
    description = "This command exports the node public key to a file.",
    mixinStandardHelpOptions = true
  )
  static class ExportSubCommand implements Runnable {

    @Option(
      names = "--to",
      required = true,
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description = "File to write public key to",
      arity = "1..1"
    )
    private final File publicKeyExportFile = null;

    @SuppressWarnings("unused")
    @ParentCommand
    private PublicKeySubCommand parentCommand; // Picocli injects reference to parent command

    @Override
    public void run() {
      checkNotNull(parentCommand);
      checkNotNull(parentCommand.parentCommand);

      final PantheonController<?> controller = parentCommand.parentCommand.buildController();
      final KeyPair keyPair = controller.getLocalNodeKeyPair();

      // this publicKeyExportFile can never be null because of Picocli arity requirement
      //noinspection ConstantConditions
      final Path path = publicKeyExportFile.toPath();

      try (final BufferedWriter fileWriter = Files.newBufferedWriter(path, UTF_8)) {
        fileWriter.write(keyPair.getPublicKey().toString());
      } catch (final IOException e) {
        LOG.error("An error occurred while trying to write the public key", e);
      }
    }
  }
}
