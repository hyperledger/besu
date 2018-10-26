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

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

// Export of the public key takes a file as parameter to export directly by writing the key in the
// file. A direct output of the key to sdt out is not done because we don't want the key value
// to be polluted by other information like logs that are in KeyPairUtil that is inevitable.
@Command(
  name = "export-pub-key",
  description = "This exports node public key in a file.",
  mixinStandardHelpOptions = true
)
class ExportPublicKeySubCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  @Parameters(arity = "1..1", paramLabel = "PATH", description = "File to write public key to")
  private final File publicKeyExportFile = null;

  @SuppressWarnings("unused")
  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @Override
  public void run() {

    final PantheonController<?> controller = parentCommand.buildController();
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
