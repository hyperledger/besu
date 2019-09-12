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
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.COMMAND_NAME;

import tech.pegasys.pantheon.chainexport.RlpBlockExporter;
import tech.pegasys.pantheon.chainimport.JsonBlockImporter;
import tech.pegasys.pantheon.chainimport.RlpBlockImporter;
import tech.pegasys.pantheon.cli.PantheonCommand;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.ExportSubCommand;
import tech.pegasys.pantheon.cli.subcommands.blocks.BlocksSubCommand.ImportSubCommand;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

  private final RlpBlockImporter rlpBlockImporter;
  private final JsonBlockImporterFactory jsonBlockImporterFactory;

  private final RlpBlockExporterFactory rlpBlockExporterFactory;

  private final PrintStream out;

  public BlocksSubCommand(
      final RlpBlockImporter rlpBlockImporter,
      final JsonBlockImporterFactory jsonBlockImporterFactory,
      final RlpBlockExporterFactory rlpBlockExporterFactory,
      final PrintStream out) {
    this.rlpBlockImporter = rlpBlockImporter;
    this.rlpBlockExporterFactory = rlpBlockExporterFactory;
    this.jsonBlockImporterFactory = jsonBlockImporterFactory;
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
    private final BlockImportFormat format = BlockImportFormat.RLP;

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
      LOG.info("Import {} block data from {}", format, blocksImportFile);

      checkCommand(parentCommand);
      checkNotNull(parentCommand.rlpBlockImporter);
      checkNotNull(parentCommand.jsonBlockImporterFactory);

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

      JsonBlockImporter<T> importer = parentCommand.jsonBlockImporterFactory.get(controller);
      final String jsonData = Files.readString(path);
      importer.importChain(jsonData);
    }

    private <T> void importRlpBlocks(final PantheonController<T> controller, final Path path)
        throws IOException {
      parentCommand.rlpBlockImporter.importBlockchain(path, controller);
    }
  }

  /**
   * blocks export sub-command
   *
   * <p>Export a block list from storage
   */
  @Command(
      name = "export",
      description = "This command exports a specific block, or list of blocks from storage.",
      mixinStandardHelpOptions = true)
  static class ExportSubCommand implements Runnable {
    @SuppressWarnings("unused")
    @ParentCommand
    private BlocksSubCommand parentCommand; // Picocli injects reference to parent command

    @Option(
        names = "--start-block",
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description = "The starting index of the block, or block list to export.",
        arity = "1..1")
    private final Long startBlock = null;

    @Option(
        names = "--end-block",
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description =
            "The ending index of the block list to export (exclusive). If not specified a single block will be exported.",
        arity = "1..1")
    private final Long endBlock = null;

    @Option(
        names = "--format",
        hidden = true,
        description =
            "The format to export, possible values are: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
        arity = "1..1")
    private final BlockExportFormat format = BlockExportFormat.RLP;

    @Option(
        names = "--to",
        required = true,
        paramLabel = MANDATORY_FILE_FORMAT_HELP,
        description = "File to write the block list to.",
        arity = "1..1")
    private File blocksExportFile = null;

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {

      LOG.info("Export {} block data to file {}", format, blocksExportFile.toPath());

      checkCommand(this, startBlock, endBlock);
      final Optional<MetricsService> metricsService = initMetrics(parentCommand);

      final PantheonController<?> controller = createPantheonController();
      try {
        switch (format) {
          case RLP:
            exportRlpFormat(controller);
            break;
          default:
            throw new ParameterException(
                spec.commandLine(), "Unsupported format: " + format.toString());
        }
      } catch (IOException e) {
        throw new ExecutionException(
            spec.commandLine(), "An error occurred while exporting blocks.", e);
      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }

    private PantheonController<?> createPantheonController() {
      return parentCommand.parentCommand.buildController();
    }

    private void exportRlpFormat(final PantheonController<?> controller) throws IOException {
      final ProtocolContext<?> context = controller.getProtocolContext();
      RlpBlockExporter exporter =
          parentCommand.rlpBlockExporterFactory.get(context.getBlockchain());
      exporter.exportBlocks(blocksExportFile, getStartBlock(), getEndBlock());
    }

    private void checkCommand(
        final ExportSubCommand exportSubCommand, final Long startBlock, final Long endBlock) {
      checkNotNull(exportSubCommand.parentCommand);

      final Optional<Long> maybeStartBlock = getStartBlock();
      final Optional<Long> maybeEndBlock = getEndBlock();

      maybeStartBlock
          .filter(blockNum -> blockNum < 0)
          .ifPresent(
              (blockNum) -> {
                throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Parameter --start-block ("
                        + blockNum
                        + ") must be greater than or equal to zero.");
              });

      maybeEndBlock
          .filter(blockNum -> blockNum < 0)
          .ifPresent(
              (blockNum) -> {
                throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Parameter --end-block ("
                        + blockNum
                        + ") must be greater than or equal to zero.");
              });

      if (maybeStartBlock.isPresent() && maybeEndBlock.isPresent()) {
        if (endBlock <= startBlock) {
          throw new CommandLine.ParameterException(
              spec.commandLine(),
              "Parameter --end-block ("
                  + endBlock
                  + ") must be greater start block ("
                  + startBlock
                  + ").");
        }
      }

      // Error if data directory is empty
      Path databasePath =
          Paths.get(
              parentCommand.parentCommand.dataDir().toAbsolutePath().toString(),
              PantheonController.DATABASE_PATH);
      File databaseDirectory = new File(databasePath.toString());
      if (!databaseDirectory.isDirectory() || databaseDirectory.list().length == 0) {
        // Empty data directory, nothing to export
        throw new CommandLine.ParameterException(
            spec.commandLine(),
            "Chain is empty.  Unable to export blocks from specified data directory: "
                + databaseDirectory.toString());
      }
    }

    private Optional<Long> getStartBlock() {
      return Optional.ofNullable(startBlock);
    }

    private Optional<Long> getEndBlock() {
      return Optional.ofNullable(endBlock);
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
  public interface JsonBlockImporterFactory {
    <T> JsonBlockImporter<T> get(PantheonController<T> controller);
  }

  @FunctionalInterface
  public interface RlpBlockExporterFactory {
    RlpBlockExporter get(Blockchain blockchain);
  }
}
