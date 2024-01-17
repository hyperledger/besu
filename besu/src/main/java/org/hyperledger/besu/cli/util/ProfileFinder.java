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
import static org.hyperledger.besu.cli.DefaultCommandValues.PROFILE_OPTION_NAME;
import static org.hyperledger.besu.cli.util.EnvironmentVariableDefaultProvider.nameToEnvVarSuffix;

import org.hyperledger.besu.cli.config.ProfileName;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

public class ProfileFinder extends AbstractConfigurationFinder<InputStream> {
  private static final String PROFILE_ENV_NAME = nameToEnvVarSuffix(CONFIG_FILE_OPTION_NAME);

  @Override
  public Optional<InputStream> findConfiguration(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult) {
    final CommandLine commandLine = parseResult.commandSpec().commandLine();

    if (isConfigSpecifiedInBothSources(environment, parseResult)) {
      throwExceptionForBothSourcesSpecified(environment, parseResult, commandLine);
    }

    if (parseResult.hasMatchedOption(PROFILE_OPTION_NAME)) {
      return getConfigFromOption(parseResult, commandLine);
    }

    if (environment.containsKey(PROFILE_ENV_NAME)) {
      return getConfigFromEnvironment(environment, commandLine);
    }
    return Optional.empty();
  }

  @Override
  public boolean isConfigSpecifiedInBothSources(
      final Map<String, String> environment, final CommandLine.ParseResult parseResult) {
    return parseResult.hasMatchedOption(PROFILE_OPTION_NAME)
        && environment.containsKey(PROFILE_ENV_NAME);
  }

  public void throwExceptionForBothSourcesSpecified(
      final Map<String, String> environment,
      final CommandLine.ParseResult parseResult,
      final CommandLine commandLine) {
    throw new CommandLine.ParameterException(
        commandLine,
        String.format(
            "Profile specified using %s=%s and %s %s",
            PROFILE_ENV_NAME,
            environment.get(PROFILE_ENV_NAME),
            PROFILE_OPTION_NAME,
            parseResult.matchedOption(PROFILE_OPTION_NAME).stringValues()));
  }

  public Optional<InputStream> getConfigFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine) {
    try {
      return getProfile(parseResult.matchedOption(PROFILE_OPTION_NAME).getter().get(), commandLine);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public Optional<InputStream> getConfigFromEnvironment(
      final Map<String, String> environment, final CommandLine commandLine) {
    return getProfile(ProfileName.valueOf(environment.get(PROFILE_ENV_NAME)), commandLine);
  }

  private static Optional<InputStream> getProfile(
      final ProfileName profileName, final CommandLine commandLine) {
    return Optional.of(getTomlFile(commandLine, profileName.getConfigFile()));
  }

  private static InputStream getTomlFile(final CommandLine commandLine, final String file) {
    InputStream resourceUrl = ProfileFinder.class.getClassLoader().getResourceAsStream(file);
    if (resourceUrl == null) {
      throw new CommandLine.ParameterException(
          commandLine, String.format("TOML file %s not found", file));
    }
    return resourceUrl;
  }
}
