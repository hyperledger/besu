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
package org.hyperledger.besu.cli.subcommands.networkcreate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.isNull;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_NETWORK_CREATE_INIT_FILE;
import static org.hyperledger.besu.cli.DefaultCommandValues.DEFAULT_NETWORK_CREATE_TARGET_DIRECTORY;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_FILE_FORMAT_HELP;
import static org.hyperledger.besu.cli.subcommands.networkcreate.NetworkCreateSubCommand.COMMAND_NAME;

import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.InitConfigurationErrorHandler;
import org.hyperledger.besu.cli.subcommands.networkcreate.mapping.MapperAdapter;
import org.hyperledger.besu.cli.subcommands.networkcreate.model.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

@Command(
    name = COMMAND_NAME,
    description =
        "Network Creator subcommand intakes an initialization file and outputs the network"
            + " genesis file, pre-configured nodes and keys.",
    mixinStandardHelpOptions = true)
public class NetworkCreateSubCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  public static final String COMMAND_NAME = "network-create";

  @SuppressWarnings("unused")
  @ParentCommand
  private BesuCommand parentCommand;

  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  @SuppressWarnings("unused")
  final PrintStream out;

  public NetworkCreateSubCommand(final PrintStream out) {
    this.out = out;
  }

  @SuppressWarnings("FieldMustBeFinal")
  @CommandLine.Option(
      names = {"--initialization-file"},
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "TOML initialisation file used to generate the network setup. (default: <data dir>init.toml)")
  private File initFile = null;

  @Option(
      names = "--to",
      paramLabel = MANDATORY_FILE_FORMAT_HELP,
      description =
          "Target directory to write the network setup and resources. (default: <data dir>/generated-network/)\"",
      arity = "1..1")
  private Path targetDirectory = null;

  @Override
  public void run() {
    checkNotNull(parentCommand);

    if(isNull(initFile)){
      initFile = parentCommand.dataDir().resolve(DEFAULT_NETWORK_CREATE_INIT_FILE).toAbsolutePath().toFile();
    }
    if(isNull(targetDirectory)){
      targetDirectory = parentCommand.dataDir().resolve(DEFAULT_NETWORK_CREATE_TARGET_DIRECTORY).toAbsolutePath();
    }

    // TODO handle exceptions
    try {
      final MapperAdapter mapper = MapperAdapter.getMapper(initFile.toURI().toURL());
      final Configuration initConfig = mapper.map(new TypeReference<>() {});
      initConfig.verify(new InitConfigurationErrorHandler());

      LOG.info("Generate target {}", targetDirectory);
      Path generatedResource = initConfig.generate(targetDirectory);
      LOG.info("Resources generated in {}", generatedResource);
    } catch (final MalformedURLException | URISyntaxException e) {
      throw new ParameterException(
          spec.commandLine(), String.format("Invalid init file path '%1$s'.", initFile), e);
    } catch (JsonProcessingException e) {
      throw new ParameterException(
          spec.commandLine(),
          String.format("Invalid initialisation content in file %1$s", initFile),
          e);
    } catch (IOException e) {
      throw new ExecutionException(
          spec.commandLine(),
          String.format(
              "Unable to read %1$s initialisation file %2$s",
              (initFile.getName().endsWith(DEFAULT_NETWORK_CREATE_INIT_FILE)
                  ? "default"
                  : "custom"),
              initFile),
          e);
    }
  }
}
