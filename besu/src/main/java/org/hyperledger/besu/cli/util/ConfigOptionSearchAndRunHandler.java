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

import org.hyperledger.besu.cli.config.ProfileName;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/** Custom Config option search and run handler. */
public class ConfigOptionSearchAndRunHandler extends CommandLine.RunLast {
  private final IExecutionStrategy resultHandler;
  private final IParameterExceptionHandler parameterExceptionHandler;
  private final Map<String, String> environment;

  /**
   * Instantiates a new Config option search and run handler.
   *
   * @param resultHandler the result handler
   * @param parameterExceptionHandler the parameter exception handler
   * @param environment the environment variables map
   */
  public ConfigOptionSearchAndRunHandler(
      final IExecutionStrategy resultHandler,
      final IParameterExceptionHandler parameterExceptionHandler,
      final Map<String, String> environment) {
    this.resultHandler = resultHandler;
    this.parameterExceptionHandler = parameterExceptionHandler;
    this.environment = environment;
  }

  @Override
  public List<Object> handle(final ParseResult parseResult) throws ParameterException {
    final CommandLine commandLine = parseResult.commandSpec().commandLine();

    final Optional<File> configFile = findConfigFile(parseResult, commandLine);
    final Optional<InputStream> profileFile = findProfileFile(parseResult, commandLine);

    commandLine.setDefaultValueProvider(
        createDefaultValueProvider(commandLine, configFile, profileFile));

    commandLine.setExecutionStrategy(resultHandler);
    commandLine.setParameterExceptionHandler(parameterExceptionHandler);
    commandLine.execute(parseResult.originalArgs().toArray(new String[0]));

    return new ArrayList<>();
  }

  private Optional<File> findConfigFile(
      final ParseResult parseResult, final CommandLine commandLine) {
    if (parseResult.hasMatchedOption("--config-file")
        && environment.containsKey("BESU_CONFIG_FILE")) {
      throw new ParameterException(
          commandLine,
          String.format(
              "TOML file specified using BESU_CONFIG_FILE=%s and --config-file %s",
              environment.get("BESU_CONFIG_FILE"),
              parseResult.matchedOption("--config-file").stringValues()));
    } else if (parseResult.hasMatchedOption("--config-file")) {
      final OptionSpec configFileOption = parseResult.matchedOption("--config-file");
      try {
        return Optional.of(configFileOption.getter().get());
      } catch (final Exception e) {
        throw new ParameterException(commandLine, e.getMessage(), e);
      }
    } else if (environment.containsKey("BESU_CONFIG_FILE")) {
      final File toml = new File(environment.get("BESU_CONFIG_FILE"));
      if (!toml.exists()) {
        throw new ParameterException(
            commandLine,
            String.format(
                "TOML file %s specified in environment variable BESU_CONFIG_FILE not found",
                environment.get("BESU_CONFIG_FILE")));
      }
      return Optional.of(toml);
    }

    return Optional.empty();
  }

  private Optional<InputStream> findProfileFile(
      final ParseResult parseResult, final CommandLine commandLine) {
    final String profileOption = "--profile";
    final String profileEnvironmentKey = "BESU_PROFILE";

    if (parseResult.hasMatchedOption(profileOption)
        && environment.containsKey(profileEnvironmentKey)) {
      throw new ParameterException(
          commandLine,
          String.format(
              "Profile specified using %s=%s and %s %s",
              profileEnvironmentKey,
              environment.get(profileEnvironmentKey),
              profileOption,
              parseResult.matchedOption(profileOption).stringValues()));
    }

    if (parseResult.hasMatchedOption(profileOption)) {
      try {
        ProfileName profileName = parseResult.matchedOption(profileOption).getter().get();
        return Optional.of(getTomlFile(commandLine, profileName.getConfigFile()));
      } catch (Exception e) {
        throw new ParameterException(commandLine, e.getMessage(), e);
      }
    }

    if (environment.containsKey(profileEnvironmentKey)) {
      try {
        ProfileName profileName = ProfileName.valueOf(environment.get(profileEnvironmentKey));
        return Optional.of(getTomlFile(commandLine, profileName.getConfigFile()));
      } catch (Exception e) {
        throw new ParameterException(commandLine, e.getMessage(), e);
      }
    }
    return Optional.empty();
  }

  private InputStream getTomlFile(final CommandLine commandLine, final String file) {
    InputStream resourceUrl = getClass().getClassLoader().getResourceAsStream(file);
    if (resourceUrl == null) {
      throw new ParameterException(commandLine, String.format("TOML file %s not found", file));
    }
    return resourceUrl;
  }

  /**
   * Create default value provider default value provider.
   *
   * @param commandLine the command line
   * @param configFile the config file
   * @return the default value provider
   */
  @VisibleForTesting
  IDefaultValueProvider createDefaultValueProvider(
      final CommandLine commandLine,
      final Optional<File> configFile,
      final Optional<InputStream> profileFile) {
    List<IDefaultValueProvider> providers = new ArrayList<>();
    providers.add(new EnvironmentVariableDefaultProvider(environment));
    configFile.ifPresent(
        config -> providers.add(TomlConfigurationDefaultProvider.fromFile(commandLine, config)));
    profileFile.ifPresent(
        profile ->
            providers.add(TomlConfigurationDefaultProvider.fromInputStream(commandLine, profile)));
    return new CascadingDefaultProvider(providers);
  }

  @Override
  public ConfigOptionSearchAndRunHandler self() {
    return this;
  }
}
