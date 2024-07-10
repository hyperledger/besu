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

import static org.hyperledger.besu.cli.DefaultCommandValues.PROFILE_OPTION_NAME;

import org.hyperledger.besu.cli.config.InternalProfileName;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  /** Default Constructor. */
  public ProfileFinder() {}

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
    final String profileName;
    try {
      profileName = parseResult.matchedOption(PROFILE_OPTION_NAME).getter().get();
    } catch (final Exception e) {
      throw new CommandLine.ParameterException(
          commandLine, "Unexpected error in obtaining value of --profile", e);
    }
    return getProfile(profileName, commandLine);
  }

  @Override
  public Optional<InputStream> getFromEnvironment(
      final Map<String, String> environment, final CommandLine commandLine) {
    return getProfile(environment.get(PROFILE_ENV_NAME), commandLine);
  }

  private static Optional<InputStream> getProfile(
      final String profileName, final CommandLine commandLine) {
    final Optional<String> internalProfileConfigPath =
        InternalProfileName.valueOfIgnoreCase(profileName).map(InternalProfileName::getConfigFile);
    if (internalProfileConfigPath.isPresent()) {
      return Optional.of(getTomlFileFromClasspath(internalProfileConfigPath.get()));
    } else {
      final Path externalProfileFile = defaultProfilesDir().resolve(profileName + ".toml");
      if (Files.exists(externalProfileFile)) {
        try {
          return Optional.of(Files.newInputStream(externalProfileFile));
        } catch (IOException e) {
          throw new CommandLine.ParameterException(
              commandLine, "Error reading external profile: " + profileName);
        }
      } else {
        throw new CommandLine.ParameterException(
            commandLine, "Unable to load external profile: " + profileName);
      }
    }
  }

  private static InputStream getTomlFileFromClasspath(final String profileConfigFile) {
    InputStream resourceUrl =
        ProfileFinder.class.getClassLoader().getResourceAsStream(profileConfigFile);
    // this is not meant to happen, because for each InternalProfileName there is a corresponding
    // TOML file in resources
    if (resourceUrl == null) {
      throw new IllegalStateException(
          String.format("Internal Profile TOML %s not found", profileConfigFile));
    }
    return resourceUrl;
  }

  /**
   * Returns the external profile names which are file names without extension in the default
   * profiles directory.
   *
   * @return Set of external profile names
   */
  public static Set<String> getExternalProfileNames() {
    final Path profilesDir = defaultProfilesDir();
    if (!Files.exists(profilesDir)) {
      return Set.of();
    }

    try (Stream<Path> pathStream = Files.list(profilesDir)) {
      return pathStream
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".toml"))
          .map(
              path ->
                  path.getFileName()
                      .toString()
                      .substring(0, path.getFileName().toString().length() - 5))
          .collect(Collectors.toSet());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Return default profiles directory location
   *
   * @return Path to default profiles directory
   */
  private static Path defaultProfilesDir() {
    final String profilesDir = System.getProperty("besu.profiles.dir");
    if (profilesDir == null) {
      return Paths.get(System.getProperty("besu.home", "."), "profiles");
    } else {
      return Paths.get(profilesDir);
    }
  }
}
