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
package org.hyperledger.besu.cli.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import picocli.CommandLine;
import picocli.CommandLine.AbstractParseResultHandler;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;

public class ConfigOptionSearchAndRunHandler extends AbstractParseResultHandler<List<Object>> {
  private static final String DOCKER_CONFIG_LOCATION = "/etc/besu/besu.conf";

  private final AbstractParseResultHandler<List<Object>> resultHandler;
  private final CommandLine.IExceptionHandler2<List<Object>> exceptionHandler;
  private final String configFileOptionName;
  private final Map<String, String> environment;
  private final boolean isDocker;

  public ConfigOptionSearchAndRunHandler(
      final AbstractParseResultHandler<List<Object>> resultHandler,
      final CommandLine.IExceptionHandler2<List<Object>> exceptionHandler,
      final String configFileOptionName,
      final Map<String, String> environment,
      final boolean isDocker) {
    this.resultHandler = resultHandler;
    this.exceptionHandler = exceptionHandler;
    this.configFileOptionName = configFileOptionName;
    this.environment = environment;
    this.isDocker = isDocker;
    // use the same output as the regular options handler to ensure that outputs are all going
    // in the same place. No need to do this for the exception handler as we reuse it directly.
    this.useOut(resultHandler.out());
  }

  @Override
  public List<Object> handle(final ParseResult parseResult) throws ExecutionException {
    final CommandLine commandLine = parseResult.asCommandLineList().get(0);
    final Optional<File> configFile = findConfigFile(parseResult, commandLine);
    commandLine.setDefaultValueProvider(createDefaultValueProvider(commandLine, configFile));
    commandLine.parseWithHandlers(
        resultHandler, exceptionHandler, parseResult.originalArgs().toArray(new String[0]));
    return new ArrayList<>();
  }

  private Optional<File> findConfigFile(
      final ParseResult parseResult, final CommandLine commandLine) {
    if (parseResult.hasMatchedOption(configFileOptionName)) {
      final OptionSpec configFileOption = parseResult.matchedOption(configFileOptionName);
      try {
        return Optional.of(configFileOption.getter().get());
      } catch (final Exception e) {
        throw new ExecutionException(commandLine, e.getMessage(), e);
      }
    } else if (isDocker) {
      final File dockerConfigFile = new File(DOCKER_CONFIG_LOCATION);
      return dockerConfigFile.exists() ? Optional.of(dockerConfigFile) : Optional.empty();
    }
    return Optional.empty();
  }

  @VisibleForTesting
  IDefaultValueProvider createDefaultValueProvider(
      final CommandLine commandLine, final Optional<File> configFile) {
    if (configFile.isPresent()) {
      return new CascadingDefaultProvider(
          new EnvironmentVariableDefaultProvider(environment),
          new TomlConfigFileDefaultProvider(commandLine, configFile.get()));
    } else {
      return new EnvironmentVariableDefaultProvider(environment);
    }
  }

  @Override
  public ConfigOptionSearchAndRunHandler self() {
    return this;
  }
}
