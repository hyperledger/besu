package net.consensys.pantheon.cli;

import static com.google.common.base.Preconditions.checkNotNull;

import net.consensys.pantheon.util.BlockImporter;

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
  private static final Logger LOGGER = LogManager.getLogger();

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
    LOGGER.info("Runs import sub command with blocksImportPath : {}", blocksImportPath);

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
