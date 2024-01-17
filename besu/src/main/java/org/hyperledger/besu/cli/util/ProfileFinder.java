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

import static org.hyperledger.besu.cli.DefaultCommandValues.PROFILE_OPTION_NAME;

import org.hyperledger.besu.cli.config.ProfileName;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

import picocli.CommandLine;

/**
 * Class for finding profile configurations. This class extends the AbstractConfigurationFinder and
 * provides methods for finding profile configurations based on command line options and environment
 * variables. Each profile corresponds to a TOML configuration file that contains settings for
 * various options. The profile to use can be specified with the '--profile' command line option or
 * the 'BESU_PROFILE' environment variable.
 */
public class ProfileFinder extends AbstractConfigurationFinder<InputStream> {
  private static final String PROFILE_ENV_NAME = "BESU_PROFILE";

  @Override
  protected String getConfigOptionName() {
    return PROFILE_OPTION_NAME;
  }

  @Override
  protected String getConfigEnvName() {
    return PROFILE_ENV_NAME;
  }

  @Override
  public Optional<InputStream> getFromOption(
      final CommandLine.ParseResult parseResult, final CommandLine commandLine) {
    try {
      return getProfile(parseResult.matchedOption(PROFILE_OPTION_NAME).getter().get(), commandLine);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Optional<InputStream> getFromEnvironment(
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
