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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.error.BesuParameterExceptionHandler;
import org.hyperledger.besu.cli.options.stable.LoggingLevelOption;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.IGetter;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.RunLast;

@RunWith(MockitoJUnitRunner.class)
public class ConfigOptionSearchAndRunHandlerTest {

  private static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  private LoggingLevelOption levelOption;
  private final IExecutionStrategy resultHandler = new RunLast();
  private IParameterExceptionHandler parameterExceptionHandler;

  private final Map<String, String> environment = singletonMap("BESU_LOGGING", "ERROR");
  private ConfigOptionSearchAndRunHandler configParsingHandler;

  @Mock ParseResult mockParseResult;
  @Mock CommandSpec mockCommandSpec;
  @Mock CommandLine mockCommandLine;
  @Mock OptionSpec mockConfigOptionSpec;
  @Mock IGetter mockConfigOptionGetter;
  @Mock BesuParameterExceptionHandler mockParameterExceptionHandler;

  @Before
  public void initMocks() {
    when(mockCommandSpec.commandLine()).thenReturn(mockCommandLine);
    when(mockParseResult.commandSpec()).thenReturn(mockCommandSpec);
    final List<String> originalArgs = new ArrayList<>();
    originalArgs.add(CONFIG_FILE_OPTION_NAME);
    when(mockParseResult.originalArgs()).thenReturn(originalArgs);
    when(mockParseResult.matchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(mockConfigOptionSpec);
    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(true);
    when(mockConfigOptionSpec.getter()).thenReturn(mockConfigOptionGetter);
    when(mockCommandLine.getParameterExceptionHandler()).thenReturn(mockParameterExceptionHandler);
    levelOption = new LoggingLevelOption();
    levelOption.setLogLevel("INFO");
    parameterExceptionHandler = mockCommandLine.getParameterExceptionHandler();
    configParsingHandler =
        new ConfigOptionSearchAndRunHandler(resultHandler, parameterExceptionHandler, environment);
  }

  @Test
  public void handleWithCommandLineOption() throws Exception {
    when(mockConfigOptionGetter.get()).thenReturn(temp.newFile());
    final List<Object> result = configParsingHandler.handle(mockParseResult);
    verify(mockCommandLine).setDefaultValueProvider(any(IDefaultValueProvider.class));
    verify(mockCommandLine).setExecutionStrategy(eq(resultHandler));
    verify(mockCommandLine).setParameterExceptionHandler(eq(parameterExceptionHandler));
    verify(mockCommandLine).execute(anyString());

    assertThat(result).isEmpty();
  }

  @Test
  public void handleWithEnvironmentVariable() throws IOException {
    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    final ConfigOptionSearchAndRunHandler environmentConfigFileParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler,
            parameterExceptionHandler,
            singletonMap("BESU_CONFIG_FILE", temp.newFile().getAbsolutePath()));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    environmentConfigFileParsingHandler.handle(mockParseResult);
  }

  @Test
  public void handleWithCommandLineOptionShouldRaiseExceptionIfNoFileParam() throws Exception {
    final String error_message = "an error occurred during get";
    when(mockConfigOptionGetter.get()).thenThrow(new Exception(error_message));
    assertThatThrownBy(() -> configParsingHandler.handle(mockParseResult))
        .isInstanceOf(Exception.class)
        .hasMessage(error_message);
  }

  @Test
  public void handleWithEnvironmentVariableOptionShouldRaiseExceptionIfNoFileParam() {
    final ConfigOptionSearchAndRunHandler environmentConfigFileParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler,
            parameterExceptionHandler,
            singletonMap("BESU_CONFIG_FILE", "not_found.toml"));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    assertThatThrownBy(() -> environmentConfigFileParsingHandler.handle(mockParseResult))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  public void shouldRetrieveConfigFromEnvironmentWhenConfigFileSpecified() throws Exception {
    final IDefaultValueProvider defaultValueProvider =
        configParsingHandler.createDefaultValueProvider(
            mockCommandLine, Optional.of(new File("foo")));
    final String value = defaultValueProvider.defaultValue(OptionSpec.builder("--logging").build());
    assertThat(value).isEqualTo("ERROR");
  }

  @Test
  public void shouldRetrieveConfigFromEnvironmentWhenConfigFileNotSpecified() throws Exception {
    final IDefaultValueProvider defaultValueProvider =
        configParsingHandler.createDefaultValueProvider(mockCommandLine, Optional.empty());
    final String value = defaultValueProvider.defaultValue(OptionSpec.builder("--logging").build());
    assertThat(value).isEqualTo("ERROR");
  }

  @Test
  public void handleThrowsErrorWithWithEnvironmentVariableAndCommandLineSpecified()
      throws IOException {

    final ConfigOptionSearchAndRunHandler environmentConfigFileParsingHandler =
        new ConfigOptionSearchAndRunHandler(
            resultHandler,
            parameterExceptionHandler,
            singletonMap("BESU_CONFIG_FILE", temp.newFile().getAbsolutePath()));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(true);

    assertThatThrownBy(() -> environmentConfigFileParsingHandler.handle(mockParseResult))
        .isInstanceOf(CommandLine.ParameterException.class);
  }
}
