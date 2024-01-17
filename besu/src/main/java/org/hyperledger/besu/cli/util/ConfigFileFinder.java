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
import static org.hyperledger.besu.cli.util.EnvironmentVariableDefaultProvider.nameToEnvVarSuffix;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

public class ConfigFileFinder extends AbstractConfigurationFinder<File> {

  private static final String CONFIG_FILE_ENV_NAME = nameToEnvVarSuffix(CONFIG_FILE_OPTION_NAME);

  @Override
  public Optional<File> findConfiguration(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult) {
    final CommandLine commandLine = parseResult.commandSpec().commandLine();
    if (isConfigSpecifiedInBothSources(environment, parseResult)) {
      throwExceptionForBothSourcesSpecified(environment, parseResult, commandLine);
    }
    if (parseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)) {
      return getConfigFromOption(parseResult, commandLine);
    }
    if (environment.containsKey(CONFIG_FILE_ENV_NAME)) {
      return getConfigFromEnvironment(environment, commandLine);
    }
    return Optional.empty();
  }

  @Override
  public boolean isConfigSpecifiedInBothSources(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult) {
    return parseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)
        && environment.containsKey(CONFIG_FILE_ENV_NAME);
  }

  @Override
  public Optional<File> getConfigFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine) {
    final CommandLine.Model.OptionSpec configFileOption =
        parseResult.matchedOption(CONFIG_FILE_OPTION_NAME);
    try {
      return Optional.of(configFileOption.getter().get());
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(commandLine, e.getMessage(), e);
    }
  }

  @Override
  public Optional<File> getConfigFromEnvironment(
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

  @Override
  public void throwExceptionForBothSourcesSpecified(
      final Map<String, String> environment,
      final CommandLine.ParseResult parseResult,
      final CommandLine commandLine) {
    throw new CommandLine.ParameterException(
        commandLine,
        String.format(
            "TOML file specified using both %s=%s and %s %s",
            CONFIG_FILE_ENV_NAME,
            CONFIG_FILE_OPTION_NAME,
            environment.get(CONFIG_FILE_ENV_NAME),
            parseResult.matchedOption(CONFIG_FILE_OPTION_NAME).stringValues()));
  }
}
