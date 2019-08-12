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
package tech.pegasys.pantheon.cli.subcommands.blocks;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.COMMAND_NAME;

import tech.pegasys.pantheon.chainimport.ChainImporter;
import tech.pegasys.pantheon.cli.PantheonCommand;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.ExportSubCommand;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.ImportSubCommand;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;
import tech.pegasys.pantheon.util.BlockExporter;
import tech.pegasys.pantheon.util.BlockImporter;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Blocks related sub-command */
@Command(
    name = COMMAND_NAME,
    description = "This command provides blocks related actions.",
    mixinStandardHelpOptions = true,
    subcommands = {ImportSubCommand.class, ExportSubCommand.class})
public class BlocksSubCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  public static final String COMMAND_NAME = "blocks";

  @SuppressWarnings("unused")
  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  private final BlockImporter blockImporter;
  private final BlockExporter blockExporter;
  private final ChainImporterFactory chainImporterFactory;

  private final PrintStream out;

  public BlocksSubCommand(
      final BlockImporter blockImporter,
      final BlockExporter blockExporter,
      final ChainImporterFactory chainImporterFactory,
      final PrintStream out) {
    this.blockImporter = blockImporter;
    this.blockExporter = blockExporter;
    this.chainImporterFactory = chainImporterFactory;
    this.out = out;
  }

  @Override
  public void run() {
    spec.commandLine().usage(out);
  }

  /**
   * blocks import sub-command
   *
   * <p>Imports blocks from a file into the database
   */
  @Command(
      name = "import",
      description = "This command imports blocks from a file into the database.",
      mixinStandardHelpOptions = true)
  static class ImportSubCommand implements Runnable {
    @SuppressWarnings("unused")
    @ParentCommand
    private BlocksSubCommand parentCommand; // Picocli injects reference to parent command

    @Option(
        names = "--from",
        required = true,
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description = "File containing blocks to import.",
        arity = "1..1")
    private final File blocksImportFile = null;

    @Option(
        names = "--format",
        hidden = true,
        description =
            "The type of data to be imported, possible values are: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
        arity = "1..1")
    private final BlockFormat format = BlockFormat.RLP;

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
      LOG.info("Import {} block data from {}", format, blocksImportFile);

      checkCommand(parentCommand);
      checkNotNull(parentCommand.blockImporter);

      Optional<MetricsService> metricsService = initMetrics(parentCommand);

      try {
        // As blocksImportFile even if initialized as null is injected by PicoCLI and param is
        // mandatory. So we are sure it's always not null, we can remove the warning.
        //noinspection ConstantConditions
        final Path path = blocksImportFile.toPath();
        final PantheonController<?> controller = createController();
        switch (format) {
          case RLP:
            importRlpBlocks(controller, path);
            break;
          case JSON:
            importJsonBlocks(controller, path);
            break;
          default:
            throw new ParameterException(
                spec.commandLine(), "Unsupported format: " + format.toString());
        }
      } catch (final FileNotFoundException e) {
        throw new ExecutionException(
            spec.commandLine(), "Could not find file to import: " + blocksImportFile);
      } catch (final IOException e) {
        throw new ExecutionException(
            spec.commandLine(), "Unable to import blocks from " + blocksImportFile, e);
      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }

    private static void checkCommand(final BlocksSubCommand parentCommand) {
      checkNotNull(parentCommand);
      checkNotNull(parentCommand.parentCommand);
    }

    private PantheonController<?> createController() {
      try {
        // Set some defaults
        return parentCommand
            .parentCommand
            .getControllerBuilder()
            .miningParameters(getMiningParameters())
            .build();
      } catch (final IOException e) {
        throw new ExecutionException(new CommandLine(parentCommand), "Invalid path", e);
      } catch (final Exception e) {
        throw new ExecutionException(new CommandLine(parentCommand), e.getMessage(), e);
      }
    }

    private MiningParameters getMiningParameters() {
      final Wei minTransactionGasPrice = Wei.ZERO;
      // Extradata and coinbase can be configured on a per-block level via the json file
      final Address coinbase = Address.ZERO;
      final BytesValue extraData = BytesValue.EMPTY;
      return new MiningParameters(coinbase, minTransactionGasPrice, extraData, false);
    }

    private <T> void importJsonBlocks(final PantheonController<T> controller, final Path path)
        throws IOException {

      ChainImporter<T> importer = parentCommand.chainImporterFactory.get(controller);
      final String jsonData = Files.readString(path);
      importer.importChain(jsonData);
    }

    private <T> void importRlpBlocks(final PantheonController<T> controller, final Path path)
        throws IOException {
      parentCommand.blockImporter.importBlockchain(path, controller);
    }
  }

  /**
   * blocks export sub-command
   *
   * <p>Export a block list from storage
   */
  @Command(
      name = "export",
      description = "This command export a specific block from storage",
      mixinStandardHelpOptions = true)
  static class ExportSubCommand implements Runnable {
    @SuppressWarnings("unused")
    @ParentCommand
    private BlocksSubCommand parentCommand; // Picocli injects reference to parent command

    @Option(
        names = "--start-block",
        required = true,
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description = "the starting index of the block list to export (inclusive)",
        arity = "1..1")
    private final Long startBlock = null;

    @Option(
        names = "--end-block",
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description =
            "the ending index of the block list to export (exclusive), "
                + "if not specified a single block will be export",
        arity = "1..1")
    private final Long endBlock = null;

    @Option(
        names = "--to",
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description = "File to write the block list instead of standard output",
        arity = "1..1")
    private File blocksExportFile = null;

    @Override
    public void run() {

      LOG.info("Runs export sub command");

      checkCommand(this, startBlock, endBlock);

      Optional<MetricsService> metricsService = initMetrics(parentCommand);

      try {

        final BlockExporter.ExportResult exportResult =
            parentCommand.blockExporter.exportBlockchain(
                parentCommand.parentCommand.buildController(), startBlock, endBlock);

        outputBlock(exportResult.blocks);

        if (exportResult.blocks.isEmpty()) {
          throw new ExecutionException(new CommandLine(this), "No block found at the given index");
        } else if (!exportResult.allBlocksAreFound) {
          throw new ExecutionException(
              new CommandLine(this),
              "Partial export due to inability to recover all requested blocks");
        }

      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }

    private static void checkCommand(
        final ExportSubCommand exportSubCommand, final Long startBlock, final Long endBlock) {
      checkNotNull(exportSubCommand.parentCommand);
      checkNotNull(exportSubCommand.parentCommand.blockExporter);
      checkNotNull(startBlock);
      if (startBlock < 0) {
        throw new CommandLine.ParameterException(
            new CommandLine(exportSubCommand),
            "--start-block must be greater than or equal to zero");
      }
      if (endBlock != null && startBlock >= endBlock) {
        throw new CommandLine.ParameterException(
            new CommandLine(exportSubCommand), "--end-block must be greater than --start-block");
      }
    }

    private void outputBlock(final List<Block> blocks) {
      if (blocksExportFile != null) {
        final Path path = blocksExportFile.toPath();
        try (final BufferedWriter fileWriter = Files.newBufferedWriter(path, UTF_8)) {
          fileWriter.write(blocks.toString());
        } catch (final IOException e) {
          throw new ExecutionException(
              new CommandLine(this), "An error occurred while trying to write the exported blocks");
        }
      } else {
        parentCommand.out.println(blocks.toString());
      }
    }
  }

  private static Optional<MetricsService> initMetrics(final BlocksSubCommand parentCommand) {
    Optional<MetricsService> metricsService = Optional.empty();
    final MetricsConfiguration metricsConfiguration =
        parentCommand.parentCommand.metricsConfiguration();
    if (metricsConfiguration.isEnabled() || metricsConfiguration.isPushEnabled()) {
      metricsService =
          Optional.of(
              MetricsService.create(
                  Vertx.vertx(),
                  metricsConfiguration,
                  parentCommand.parentCommand.getMetricsSystem()));
      metricsService.ifPresent(MetricsService::start);
    }
    return metricsService;
  }

  @FunctionalInterface
  public interface ChainImporterFactory {
    <T> ChainImporter<T> get(PantheonController<T> controller);
  }
}
