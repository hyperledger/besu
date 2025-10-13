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

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.error.BesuParameterExceptionHandler;
import org.hyperledger.besu.cli.options.LoggingLevelOption;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.IExecutionStrategy;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.IGetter;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.RunLast;

@ExtendWith(MockitoExtension.class)
public class ConfigDefaultValueProviderStrategyTest {

  private static final String CONFIG_FILE_OPTION_NAME = "--config-file";
  @TempDir public Path temp;

  private LoggingLevelOption levelOption;
  private final IExecutionStrategy resultHandler = new RunLast();

  private final Map<String, String> environment = singletonMap("BESU_LOGGING", "ERROR");
  private ConfigDefaultValueProviderStrategy configParsingHandler;

  @Mock ParseResult mockParseResult;
  @Mock CommandSpec mockCommandSpec;
  @Mock CommandLine mockCommandLine;
  @Mock OptionSpec mockConfigOptionSpec;
  @Mock IGetter mockConfigOptionGetter;
  @Mock BesuParameterExceptionHandler mockParameterExceptionHandler;

  @BeforeEach
  public void initMocks() {
    lenient().when(mockCommandSpec.commandLine()).thenReturn(mockCommandLine);
    lenient().when(mockParseResult.commandSpec()).thenReturn(mockCommandSpec);
    final List<String> originalArgs = new ArrayList<>();
    originalArgs.add(CONFIG_FILE_OPTION_NAME);
    lenient().when(mockParseResult.originalArgs()).thenReturn(originalArgs);
    lenient()
        .when(mockParseResult.matchedOption(CONFIG_FILE_OPTION_NAME))
        .thenReturn(mockConfigOptionSpec);
    lenient().when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(true);
    lenient().when(mockConfigOptionSpec.getter()).thenReturn(mockConfigOptionGetter);
    levelOption = LoggingLevelOption.create();
    levelOption.setLogLevel("INFO");
    configParsingHandler = new ConfigDefaultValueProviderStrategy(resultHandler, environment);
  }

  @Test
  public void handleWithCommandLineOption() throws Exception {
    when(mockConfigOptionGetter.get()).thenReturn(Files.createTempFile("tmp", "txt").toFile());
    configParsingHandler.execute(mockParseResult);
    verify(mockCommandLine).setDefaultValueProvider(any(IDefaultValueProvider.class));
    verify(mockCommandLine).setExecutionStrategy(eq(resultHandler));
    verify(mockCommandLine).execute(anyString());
  }

  @Test
  public void handleWithEnvironmentVariable() throws IOException {
    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    final ConfigDefaultValueProviderStrategy environmentConfigFileParsingHandler =
        new ConfigDefaultValueProviderStrategy(
            resultHandler,
            singletonMap(
                "BESU_CONFIG_FILE",
                Files.createFile(temp.resolve("tmp")).toFile().getAbsolutePath()));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    environmentConfigFileParsingHandler.execute(mockParseResult);
  }

  @Test
  public void handleWithCommandLineOptionShouldRaiseExceptionIfNoFileParam() throws Exception {
    final String error_message = "an error occurred during get";
    when(mockConfigOptionGetter.get()).thenThrow(new Exception(error_message));
    assertThatThrownBy(() -> configParsingHandler.execute(mockParseResult))
        .isInstanceOf(Exception.class)
        .hasMessage(error_message);
  }

  @Test
  public void handleWithEnvironmentVariableOptionShouldRaiseExceptionIfNoFileParam() {
    final ConfigDefaultValueProviderStrategy environmentConfigFileParsingHandler =
        new ConfigDefaultValueProviderStrategy(
            resultHandler, singletonMap("BESU_CONFIG_FILE", "not_found.toml"));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(false);

    assertThatThrownBy(() -> environmentConfigFileParsingHandler.execute(mockParseResult))
        .isInstanceOf(CommandLine.ParameterException.class);
  }

  @Test
  public void shouldRetrieveConfigFromEnvironmentWhenConfigFileSpecified() throws Exception {
    final IDefaultValueProvider defaultValueProvider =
        configParsingHandler.createDefaultValueProvider(
            mockCommandLine, Optional.of(new File("foo")), Optional.empty());
    final String value = defaultValueProvider.defaultValue(OptionSpec.builder("--logging").build());
    assertThat(value).isEqualTo("ERROR");
  }

  @Test
  public void shouldRetrieveConfigFromEnvironmentWhenConfigFileNotSpecified() throws Exception {
    final IDefaultValueProvider defaultValueProvider =
        configParsingHandler.createDefaultValueProvider(
            mockCommandLine, Optional.empty(), Optional.empty());
    final String value = defaultValueProvider.defaultValue(OptionSpec.builder("--logging").build());
    assertThat(value).isEqualTo("ERROR");
  }

  @Test
  public void handleThrowsErrorWithWithEnvironmentVariableAndCommandLineSpecified()
      throws IOException {

    final ConfigDefaultValueProviderStrategy environmentConfigFileParsingHandler =
        new ConfigDefaultValueProviderStrategy(
            resultHandler,
            singletonMap("BESU_CONFIG_FILE", temp.resolve("tmp").toFile().getAbsolutePath()));

    when(mockParseResult.hasMatchedOption(CONFIG_FILE_OPTION_NAME)).thenReturn(true);

    assertThatThrownBy(() -> environmentConfigFileParsingHandler.execute(mockParseResult))
        .isInstanceOf(CommandLine.ParameterException.class);
  }
}
