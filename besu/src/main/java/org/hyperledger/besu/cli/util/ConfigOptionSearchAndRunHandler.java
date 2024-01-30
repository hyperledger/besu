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

    commandLine.setDefaultValueProvider(
        createDefaultValueProvider(
            commandLine,
            new ConfigFileFinder().findConfiguration(environment, parseResult),
            new ProfileFinder().findConfiguration(environment, parseResult)));

    commandLine.setExecutionStrategy(resultHandler);
    commandLine.setParameterExceptionHandler(parameterExceptionHandler);
    commandLine.execute(parseResult.originalArgs().toArray(new String[0]));

    return new ArrayList<>();
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
      final Optional<InputStream> profile) {
    List<IDefaultValueProvider> providers = new ArrayList<>();
    providers.add(new EnvironmentVariableDefaultProvider(environment));

    configFile.ifPresent(
        config -> {
          if (config.exists()) {
            providers.add(TomlConfigurationDefaultProvider.fromFile(commandLine, config));
          }
        });

    profile.ifPresent(
        p -> providers.add(TomlConfigurationDefaultProvider.fromInputStream(commandLine, p)));
    return new CascadingDefaultProvider(providers);
  }

  @Override
  public ConfigOptionSearchAndRunHandler self() {
    return this;
  }
}
