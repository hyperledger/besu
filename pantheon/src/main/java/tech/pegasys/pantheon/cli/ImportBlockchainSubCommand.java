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
  name = "import-blockchain",
  description =
      "This command imports blocks from a file into the database.  WARNING: This import is ALPHA and does not include comprehensive validations yet.",
  mixinStandardHelpOptions = true
)
class ImportBlockchainSubCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @Parameters(arity = "1..1", paramLabel = "PATH", description = "File containing blocks to import")
  private final Path blocksImportPath = null;

  @CommandLine.Option(
    names = {"--skip-header-validation"},
    description = "WARNING: Set only if the import file is pre-validated."
  )
  private final Boolean isSkipHeaderValidation = false;

  @CommandLine.Option(
    names = {"--metrics-interval-sec"},
    description = "seconds between logging progress metrics.",
    defaultValue = "30"
  )
  private final Integer metricsIntervalSec = 30;

  @CommandLine.Option(
    names = {"--account-commit-interval"},
    description = "commit account state every n accounts.",
    defaultValue = "100000"
  )
  private final Integer accountCommitInterval = 100_000;

  @CommandLine.Option(
    names = {"--skip-blocks"},
    description = "skip processing the blocks in the import file",
    defaultValue = "false"
  )
  private final Boolean isSkipBlocks = Boolean.FALSE;

  @CommandLine.Option(
    names = {"--skip-accounts"},
    description = "skip processing the accounts in the import file",
    defaultValue = "false"
  )
  private final Boolean isSkipAccounts = Boolean.FALSE;

  @CommandLine.Option(
    names = {"--start-of-world-state"},
    description =
        "file offset for the starting byte of the world state. Only relevant in combination with --skip-blocks."
  )
  private final Long worldStateOffset = null;

  public ImportBlockchainSubCommand() {}

  @Override
  public void run() {
    LOG.info("Runs import sub command with blocksImportPath : {}", blocksImportPath);

    checkNotNull(parentCommand);

    checkNotNull(isSkipHeaderValidation);
    checkNotNull(isSkipBlocks);
    checkNotNull(isSkipAccounts);

    try {
      final BlockImporter.ImportResult result =
          parentCommand.blockchainImporter.importBlockchain(
              blocksImportPath,
              parentCommand.buildController(),
              isSkipHeaderValidation,
              metricsIntervalSec,
              accountCommitInterval,
              isSkipBlocks,
              isSkipAccounts,
              worldStateOffset);
      System.out.println(result);
    } catch (final IOException e) {
      throw new ExecutionException(
          new CommandLine(this),
          String.format("Unable to import blocks from $1%s", blocksImportPath));
    }
  }
}
