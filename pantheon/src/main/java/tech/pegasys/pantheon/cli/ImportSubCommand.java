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

import tech.pegasys.pantheon.util.BlockImporter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
  name = "import",
  description = "This command imports blocks from a file into the database.",
  mixinStandardHelpOptions = true
)
class ImportSubCommand implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @Parameters(arity = "1..1", paramLabel = "PATH", description = "File containing blocks to import")
  private final Path blocksImportPath = null;

  private final BlockImporter blockImporter;

  ImportSubCommand(final BlockImporter blockImporter) {
    this.blockImporter = blockImporter;
  }

  @Override
  public void run() {
    LOG.info("Runs import sub command with blocksImportPath : {}", blocksImportPath);

    checkNotNull(parentCommand);
    checkNotNull(blockImporter);

    try {
      blockImporter.importBlockchain(blocksImportPath, parentCommand.buildController());
    } catch (final FileNotFoundException e) {
      throw new ExecutionException(
          new CommandLine(this), "Could not find file to import: " + blocksImportPath);
    } catch (final IOException e) {
      throw new ExecutionException(
          new CommandLine(this), "Unable to import blocks from " + blocksImportPath, e);
    }
  }
}
