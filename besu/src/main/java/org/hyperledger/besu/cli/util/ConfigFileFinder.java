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
package org.hyperledger.besu.cli.util;

import static org.hyperledger.besu.cli.DefaultCommandValues.CONFIG_FILE_OPTION_NAME;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

public class ConfigFileFinder extends AbstractConfigurationFinder<File> {
  private static final String CONFIG_FILE_ENV_NAME = "BESU_CONFIG_FILE";

  @Override
  protected String getConfigOptionName() {
    return CONFIG_FILE_OPTION_NAME;
  }

  @Override
  protected String getConfigEnvName() {
    return CONFIG_FILE_ENV_NAME;
  }

  @Override
  public Optional<File> getFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine) {
    final CommandLine.Model.OptionSpec configFileOption =
        parseResult.matchedOption(CONFIG_FILE_OPTION_NAME);
    try {
      File file = configFileOption.getter().get();
      if (!file.exists()) {
        throw new CommandLine.ParameterException(
            commandLine, "Unable to read TOML configuration, file not found.");
      }
      return Optional.of(file);
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(commandLine, e.getMessage(), e);
    }
  }

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
