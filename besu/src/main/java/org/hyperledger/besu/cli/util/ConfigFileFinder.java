/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.util;

import static org.hyperledger.besu.cli.DefaultCommandValues.CONFIG_FILE_OPTION_NAME;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

/**
 * Class for finding configuration files. This class extends the AbstractConfigurationFinder and
 * provides methods for finding configuration files based on command line options and environment
 * variables.
 */
public class ConfigFileFinder extends AbstractConfigurationFinder<File> {
  private static final String CONFIG_FILE_ENV_NAME = "BESU_CONFIG_FILE";

  /** Default constructor. */
  public ConfigFileFinder() {}

  /**
   * Returns the name of the configuration option.
   *
   * @return the name of the configuration option
   */
  @Override
  protected String getConfigOptionName() {
    return CONFIG_FILE_OPTION_NAME;
  }

  /**
   * Returns the name of the environment variable for the configuration.
   *
   * @return the name of the environment variable for the configuration
   */
  @Override
  protected String getConfigEnvName() {
    return CONFIG_FILE_ENV_NAME;
  }

  /**
   * Gets the configuration file from the command line option.
   *
   * @param parseResult the command line parse result
   * @param commandLine the command line
   * @return an Optional containing the configuration file, or an empty Optional if the
   *     configuration file was not specified in the command line option
   */
  @Override
  public Optional<File> getFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine) {
    final CommandLine.Model.OptionSpec configFileOption =
        parseResult.matchedOption(CONFIG_FILE_OPTION_NAME);
    try {
      File file = configFileOption.getter().get();
      if (!file.exists()) {
        throw new CommandLine.ParameterException(
            commandLine,
            String.format("Unable to read TOML configuration, file not found: %s", file));
      }
      return Optional.of(file);
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(commandLine, e.getMessage(), e);
    }
  }

  /**
   * Gets the configuration file from the environment variable.
   *
   * @param environment the environment variables
   * @param commandLine the command line
   * @return an Optional containing the configuration file, or an empty Optional if the
   *     configuration file was not specified in the environment variable
   */
  @Override
  public Optional<File> getFromEnvironment(
      final Map<String, String> environment, final CommandLine commandLine) {
    final File toml = new File(environment.get(CONFIG_FILE_ENV_NAME));
    if (!toml.exists()) {
      throw new CommandLine.ParameterException(
          commandLine,
          String.format(
              "TOML file %s specified in environment variable %s not found",
              CONFIG_FILE_ENV_NAME, environment.get(CONFIG_FILE_ENV_NAME)));
    }
    return Optional.of(toml);
  }
}
