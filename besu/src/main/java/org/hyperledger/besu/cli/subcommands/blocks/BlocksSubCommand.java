/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.cli.subcommands.blocks;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.COMMAND_NAME;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_MAX_OMMERS_DEPTH;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_POW_JOB_TTL;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_LIMIT;
import static org.hyperledger.besu.ethereum.core.MiningParameters.DEFAULT_REMOTE_SEALERS_TTL;

import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.ExportSubCommand;
import org.hyperledger.besu.cli.subcommands.blocks.BlocksSubCommand.ImportSubCommand;
import org.hyperledger.besu.cli.util.VersionProvider;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.IncrementingNonceGenerator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.metrics.MetricsService;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Blocks related sub-command */
@Command(
    name = COMMAND_NAME,
    description = "This command provides blocks related actions.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    subcommands = {ImportSubCommand.class, ExportSubCommand.class})
public class BlocksSubCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(BlocksSubCommand.class);

  public static final String COMMAND_NAME = "blocks";

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  private final Supplier<RlpBlockImporter> rlpBlockImporter;
  private final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory;
  private final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory;

  private final PrintStream out;

  public BlocksSubCommand(
      final Supplier<RlpBlockImporter> rlpBlockImporter,
      final Function<BesuController, JsonBlockImporter> jsonBlockImporterFactory,
      final Function<Blockchain, RlpBlockExporter> rlpBlockExporterFactory,
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
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class ImportSubCommand implements Runnable {
    @SuppressWarnings("unused")
    @ParentCommand
    private BlocksSubCommand parentCommand; // Picocli injects reference to parent command

    @Parameters(
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = "Files containing blocks to import.",
        arity = "0..*")
    private final List<Path> blockImportFiles = new ArrayList<>();

    @Option(
        names = "--from",
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = "File containing blocks to import.",
        arity = "0..*")
    private final List<Path> blockImportFileOption = new ArrayList<>();

    @Option(
        names = "--format",
        description =
            "The type of data to be imported, possible values are: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
        arity = "1..1")
    private final BlockImportFormat format = BlockImportFormat.RLP;

    @Option(
        names = "--start-time",
        description =
            "The timestamp in seconds of the first block for JSON imports. Subsequent blocks will be 1 second later. (default: current time)",
        arity = "1..1")
    private final Long startTime = System.currentTimeMillis() / 1000;

    @Option(
        names = "--skip-pow-validation-enabled",
        description = "Skip proof of work validation when importing.")
    private final Boolean skipPow = false;

    @Option(names = "--run", description = "Start besu after importing.")
    private final Boolean runBesu = false;

    @Option(
        names = "--start-block",
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description =
            "The starting index of the block, or block list to import.  If not specified all blocks before the end block will be imported",
        arity = "1..1")
    private final Long startBlock = 0L;

    @Option(
        names = "--end-block",
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description =
            "The ending index of the block list to import (exclusive).  If not specified all blocks after the start block will be imported.",
        arity = "1..1")
    private final Long endBlock = Long.MAX_VALUE;

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
      parentCommand.parentCommand.configureLogging(false);
      blockImportFiles.addAll(blockImportFileOption);

      checkCommand(parentCommand);
      checkNotNull(parentCommand.rlpBlockImporter);
      checkNotNull(parentCommand.jsonBlockImporterFactory);
      if (blockImportFiles.isEmpty()) {
        throw new ParameterException(spec.commandLine(), "No files specified to import.");
      }
      if (skipPow && format.equals(BlockImportFormat.JSON)) {
        throw new ParameterException(
            spec.commandLine(), "Can't skip proof of work validation for JSON blocks");
      }
      LOG.info("Import {} block data from {} files", format, blockImportFiles.size());
      final Optional<MetricsService> metricsService = initMetrics(parentCommand);

      try (final BesuController controller = createController()) {
        for (final Path path : blockImportFiles) {
          try {
            LOG.info("Importing from {}", path);
            switch (format) {
              case RLP:
                importRlpBlocks(controller, path);
                break;
              case JSON:
                importJsonBlocks(controller, path);
                break;
            }
          } catch (final FileNotFoundException e) {
            if (blockImportFiles.size() == 1) {
              throw new ExecutionException(
                  spec.commandLine(), "Could not find file to import: " + path);
            } else {
              LOG.error("Could not find file to import: {}", path);
            }
          } catch (final Exception e) {
            if (blockImportFiles.size() == 1) {
              throw new ExecutionException(
                  spec.commandLine(), "Unable to import blocks from " + path, e);
            } else {
              LOG.error("Unable to import blocks from " + path, e);
            }
          }
        }

        if (runBesu) {
          parentCommand.parentCommand.run();
        }
      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }

    private static void checkCommand(final BlocksSubCommand parentCommand) {
      checkNotNull(parentCommand);
      checkNotNull(parentCommand.parentCommand);
    }

    private BesuController createController() {
      try {
        // Set some defaults
        return parentCommand
            .parentCommand
            .getControllerBuilder()
            // set to mainnet genesis block so validation rules won't reject it.
            .clock(Clock.fixed(Instant.ofEpochSecond(startTime), ZoneOffset.UTC))
            .miningParameters(getMiningParameters())
            .build();
      } catch (final Exception e) {
        throw new ExecutionException(parentCommand.spec.commandLine(), e.getMessage(), e);
      }
    }

    private MiningParameters getMiningParameters() {
      final Wei minTransactionGasPrice = Wei.ZERO;
      // Extradata and coinbase can be configured on a per-block level via the json file
      final Address coinbase = Address.ZERO;
      final Bytes extraData = Bytes.EMPTY;
      return new MiningParameters.Builder()
          .coinbase(coinbase)
          .minTransactionGasPrice(minTransactionGasPrice)
          .extraData(extraData)
          .miningEnabled(false)
          .stratumMiningEnabled(false)
          .stratumNetworkInterface("0.0.0.0")
          .stratumPort(8008)
          .stratumExtranonce("080c")
          .maybeNonceGenerator(new IncrementingNonceGenerator(0))
          .minBlockOccupancyRatio(0.0)
          .remoteSealersLimit(DEFAULT_REMOTE_SEALERS_LIMIT)
          .remoteSealersTimeToLive(DEFAULT_REMOTE_SEALERS_TTL)
          .powJobTimeToLive(DEFAULT_POW_JOB_TTL)
          .maxOmmerDepth(DEFAULT_MAX_OMMERS_DEPTH)
          .build();
    }

    private void importJsonBlocks(final BesuController controller, final Path path)
        throws IOException {

      final JsonBlockImporter importer = parentCommand.jsonBlockImporterFactory.apply(controller);
      final String jsonData = Files.readString(path);
      importer.importChain(jsonData);
    }

    private void importRlpBlocks(final BesuController controller, final Path path)
        throws IOException {
      parentCommand
          .rlpBlockImporter
          .get()
          .importBlockchain(path, controller, skipPow, startBlock, endBlock);
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
      mixinStandardHelpOptions = true,
      versionProvider = VersionProvider.class)
  static class ExportSubCommand implements Runnable {
    @SuppressWarnings("unused")
    @ParentCommand
    private BlocksSubCommand parentCommand; // Picocli injects reference to parent command

    @Option(
        names = "--start-block",
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
        description = "The starting index of the block, or block list to export.",
        arity = "1..1")
    private final Long startBlock = null;

    @Option(
        names = "--end-block",
        paramLabel = DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP,
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
        paramLabel = DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP,
        description = "File to write the block list to.",
        arity = "1..1")
    private final File blocksExportFile = null;

    @SuppressWarnings("unused")
    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
      parentCommand.parentCommand.configureLogging(false);
      LOG.info("Export {} block data to file {}", format, blocksExportFile.toPath());

      checkCommand(this, startBlock, endBlock);
      final Optional<MetricsService> metricsService = initMetrics(parentCommand);

      final BesuController controller = createBesuController();
      try {
        if (format == BlockExportFormat.RLP) {
          exportRlpFormat(controller);
        } else {
          throw new ParameterException(
              spec.commandLine(), "Unsupported format: " + format.toString());
        }
      } catch (final IOException e) {
        throw new ExecutionException(
            spec.commandLine(), "An error occurred while exporting blocks.", e);
      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }

    private BesuController createBesuController() {
      return parentCommand.parentCommand.buildController();
    }

    private void exportRlpFormat(final BesuController controller) throws IOException {
      final ProtocolContext context = controller.getProtocolContext();
      final RlpBlockExporter exporter =
          parentCommand.rlpBlockExporterFactory.apply(context.getBlockchain());
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
      final Path databasePath =
          Paths.get(
              parentCommand.parentCommand.dataDir().toAbsolutePath().toString(),
              BesuController.DATABASE_PATH);
      final File databaseDirectory = new File(databasePath.toString());
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
    final MetricsConfiguration metricsConfiguration =
        parentCommand.parentCommand.metricsConfiguration();

    Optional<MetricsService> metricsService =
        MetricsService.create(
            Vertx.vertx(), metricsConfiguration, parentCommand.parentCommand.getMetricsSystem());
    metricsService.ifPresent(MetricsService::start);
    return metricsService;
  }
}
