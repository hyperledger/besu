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
import static tech.pegasys.pantheon.cli.BlocksSubCommand.COMMAND_NAME;
import static tech.pegasys.pantheon.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;

import tech.pegasys.pantheon.cli.BlocksSubCommand.ImportSubCommand;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsService;
import tech.pegasys.pantheon.util.BlockImporter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/** Blocks related sub-command */
@Command(
    name = COMMAND_NAME,
    description = "This command provides blocks related actions.",
    mixinStandardHelpOptions = true,
    subcommands = {ImportSubCommand.class})
class BlocksSubCommand implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  static final String COMMAND_NAME = "blocks";

  @SuppressWarnings("unused")
  @ParentCommand
  private PantheonCommand parentCommand; // Picocli injects reference to parent command

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec; // Picocli injects reference to command spec

  private final BlockImporter blockImporter;
  private final PrintStream out;

  BlocksSubCommand(final BlockImporter blockImporter, final PrintStream out) {
    this.blockImporter = blockImporter;
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
        description = "File containing blocks to import",
        arity = "1..1")
    private final File blocksImportFile = null;

    @Override
    public void run() {
      LOG.info("Runs import sub command with blocksImportFile : {}", blocksImportFile);

      checkNotNull(parentCommand);
      checkNotNull(parentCommand.parentCommand);
      checkNotNull(parentCommand.blockImporter);

      Optional<MetricsService> metricsService = Optional.empty();
      try {
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

        // As blocksImportFile even if initialized as null is injected by PicoCLI and param is
        // mandatory
        // So we are sure it's always not null, we can remove the warning
        //noinspection ConstantConditions
        final Path path = blocksImportFile.toPath();

        parentCommand.blockImporter.importBlockchain(
            path, parentCommand.parentCommand.buildController());
      } catch (final FileNotFoundException e) {
        throw new ExecutionException(
            new CommandLine(this), "Could not find file to import: " + blocksImportFile);
      } catch (final IOException e) {
        throw new ExecutionException(
            new CommandLine(this), "Unable to import blocks from " + blocksImportFile, e);
      } finally {
        metricsService.ifPresent(MetricsService::stop);
      }
    }
  }
}
