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
package org.hyperledger.besu.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.util.CommandLineUtils.getOptionValueOrDefault;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.util.CommandLineUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@link CommandLineUtils} focusing on the retrieval of option values
 * (getOptionValueOrDefault).
 */
public class CommandLineUtilsDefaultsTest {
  private static final String OPTION_NAME = "option";
  private static final String OPTION_VALUE = "optionValue";
  private static final String DEFAULT_VALUE = "defaultValue";
  public final CommandLine.ITypeConverter<String> converter = String::valueOf;
  private CommandLine commandLine;
  private CommandLine.Model.OptionSpec optionSpec;
  private CommandLine.IDefaultValueProvider defaultValueProvider;
  private CommandLine.ParseResult parseResult;

  @BeforeEach
  public void setUp() {
    commandLine = mock(CommandLine.class);
    parseResult = mock(CommandLine.ParseResult.class);
    CommandLine.Model.CommandSpec commandSpec = mock(CommandLine.Model.CommandSpec.class);
    optionSpec = mock(CommandLine.Model.OptionSpec.class);
    defaultValueProvider = mock(CommandLine.IDefaultValueProvider.class);
    when(commandLine.getParseResult()).thenReturn(parseResult);
    when(commandLine.getCommandSpec()).thenReturn(commandSpec);
    when(commandLine.getDefaultValueProvider()).thenReturn(defaultValueProvider);
    when(parseResult.matchedOptionValue(anyString(), any())).thenCallRealMethod();
    when(commandSpec.findOption(OPTION_NAME)).thenReturn(optionSpec);
  }

  @Test
  public void testGetOptionValueOrDefault_UserProvidedValue() {
    when(parseResult.matchedOption(OPTION_NAME)).thenReturn(optionSpec);
    when(optionSpec.getValue()).thenReturn(OPTION_VALUE);

    String result = getOptionValueOrDefault(commandLine, OPTION_NAME, converter);
    assertThat(result).isEqualTo(OPTION_VALUE);
  }

  @Test
  public void testGetOptionValueOrDefault_DefaultValue() throws Exception {
    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn(DEFAULT_VALUE);
    String result = getOptionValueOrDefault(commandLine, OPTION_NAME, converter);
    assertThat(result).isEqualTo(DEFAULT_VALUE);
  }

  @Test
  public void userOptionOverridesDefaultValue() throws Exception {
    when(parseResult.matchedOption(OPTION_NAME)).thenReturn(optionSpec);
    when(optionSpec.getValue()).thenReturn(OPTION_VALUE);

    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn(DEFAULT_VALUE);
    String result = getOptionValueOrDefault(commandLine, OPTION_NAME, converter);
    assertThat(result).isEqualTo(OPTION_VALUE);
  }

  @Test
  public void testGetOptionValueOrDefault_NoValueOrDefault() {
    String result = getOptionValueOrDefault(commandLine, OPTION_NAME, converter);
    assertThat(result).isNull();
  }

  @Test
  public void testGetOptionValueOrDefault_ConversionFailure() throws Exception {
    when(defaultValueProvider.defaultValue(optionSpec)).thenReturn(DEFAULT_VALUE);

    CommandLine.ITypeConverter<Integer> failingConverter =
        value -> {
          throw new Exception("Conversion failed");
        };

    String actualMessage =
        assertThrows(
                RuntimeException.class,
                () -> getOptionValueOrDefault(commandLine, OPTION_NAME, failingConverter))
            .getMessage();
    final String expectedMessage =
        "Failed to convert default value for option option: Conversion failed";
    assertThat(actualMessage).isEqualTo(expectedMessage);
  }
}
