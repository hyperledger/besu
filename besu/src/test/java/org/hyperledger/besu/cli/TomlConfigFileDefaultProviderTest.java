/*
 * Copyright ConsenSys AG.
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.cli.util.TomlConfigFileDefaultProvider;
import org.hyperledger.besu.datatypes.Wei;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

@RunWith(MockitoJUnitRunner.class)
public class TomlConfigFileDefaultProviderTest {
  @Mock CommandLine mockCommandLine;

  @Mock CommandSpec mockCommandSpec;

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void defaultValueForMatchingKey() throws IOException {
    when(mockCommandLine.getCommandSpec()).thenReturn(mockCommandSpec);
    Map<String, OptionSpec> validOptionsMap = new HashMap<>();
    validOptionsMap.put("--a-short-option", null);
    validOptionsMap.put("--an-actual-long-option", null);
    validOptionsMap.put("--a-longer-option", null);
    when(mockCommandSpec.optionsMap()).thenReturn(validOptionsMap);

    final File tempConfigFile = temp.newFile("config.toml");
    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("a-short-option='123'");
      fileWriter.newLine();
      fileWriter.write("an-actual-long-option=" + Long.MAX_VALUE);
      fileWriter.newLine();
      fileWriter.write("a-longer-option='1234'");
      fileWriter.flush();

      final TomlConfigFileDefaultProvider providerUnderTest =
          new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

      // this option must be found in config
      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-short-option").type(Integer.class).build()))
          .isEqualTo("123");

      // this option must be found in config as one of its names is present in the file.
      // also this is the shortest one.
      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-short-option", "another-name-for-the-option")
                      .type(Integer.class)
                      .build()))
          .isEqualTo("123");

      // this option must be found in config as one of its names is present in the file.
      // also this is a long.
      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("an-actual-long-option", "another-name-for-the-option")
                      .type(Long.class)
                      .build()))
          .isEqualTo(String.valueOf(Long.MAX_VALUE));

      // this option must be found in config as one of its names is present in the file.
      // also this is the longest one.
      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("l", "longer", "a-longer-option").type(Integer.class).build()))
          .isEqualTo("1234");
    }
  }

  @Test
  public void defaultValueForOptionMustMatchType() throws IOException {
    when(mockCommandLine.getCommandSpec()).thenReturn(mockCommandSpec);
    Map<String, OptionSpec> validOptionsMap = new HashMap<>();
    validOptionsMap.put("--a-boolean-option", null);
    validOptionsMap.put("--another-boolean-option", null);
    validOptionsMap.put("--a-primitive-boolean-option", null);
    validOptionsMap.put("--another-primitive-boolean-option", null);
    validOptionsMap.put("--a-multi-value-option", null);
    validOptionsMap.put("--an-int-value-option", null);
    validOptionsMap.put("--a-primitive-int-value-option", null);
    validOptionsMap.put("--a-wei-value-option", null);
    validOptionsMap.put("--a-string-value-option", null);
    validOptionsMap.put("--a-nested-multi-value-option", null);

    when(mockCommandSpec.optionsMap()).thenReturn(validOptionsMap);

    final File tempConfigFile = temp.newFile("config.toml");
    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("a-boolean-option=true");
      fileWriter.newLine();
      fileWriter.write("another-boolean-option=false");
      fileWriter.newLine();
      fileWriter.write("a-primitive-boolean-option=true");
      fileWriter.newLine();
      fileWriter.write("another-primitive-boolean-option=false");
      fileWriter.newLine();
      fileWriter.write("a-multi-value-option=[\"value1\", \"value2\"]");
      fileWriter.newLine();
      fileWriter.write("an-int-value-option=123");
      fileWriter.newLine();
      fileWriter.write("a-primitive-int-value-option=456");
      fileWriter.newLine();
      fileWriter.write("a-wei-value-option=1");
      fileWriter.newLine();
      fileWriter.write("a-string-value-option='my value'");
      fileWriter.newLine();
      fileWriter.write(
          "a-nested-multi-value-option=[ [\"value1\", \"value2\"], [\"value3\", \"value4\"] ]");
      fileWriter.flush();

      final TomlConfigFileDefaultProvider providerUnderTest =
          new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-boolean-option").type(Boolean.class).build()))
          .isEqualTo("true");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("another-boolean-option").type(Boolean.class).build()))
          .isEqualTo("false");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-primitive-boolean-option").type(boolean.class).build()))
          .isEqualTo("true");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("another-primitive-boolean-option")
                      .type(boolean.class)
                      .build()))
          .isEqualTo("false");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-multi-value-option").type(Collection.class).build()))
          .isEqualTo("value1,value2");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("an-int-value-option").type(Integer.class).build()))
          .isEqualTo("123");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-primitive-int-value-option").type(int.class).build()))
          .isEqualTo("456");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-wei-value-option").type(Wei.class).build()))
          .isEqualTo("1");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-string-value-option").type(String.class).build()))
          .isEqualTo("my value");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-nested-multi-value-option").type(Collection.class).build()))
          .isEqualTo("[value1,value2],[value3,value4]");
    }
  }

  @Test
  public void configFileNotFoundMustThrow() {

    final File nonExistingFile = new File("doesnt.exit");

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, nonExistingFile);

    assertThatThrownBy(
            () ->
                providerUnderTest.defaultValue(
                    OptionSpec.builder("an-option").type(String.class).build()))
        .isInstanceOf(ParameterException.class)
        .hasMessage("Unable to read TOML configuration, file not found.");
  }

  @Test
  public void invalidConfigMustThrow() throws IOException {

    final File tempConfigFile = temp.newFile("config.toml");

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

    assertThatThrownBy(
            () ->
                providerUnderTest.defaultValue(
                    OptionSpec.builder("an-option").type(String.class).build()))
        .isInstanceOf(ParameterException.class)
        .hasMessageContaining("Unable to read TOML configuration file");
  }

  @Test
  public void invalidConfigContentMustThrow() throws IOException {

    final File tempConfigFile = temp.newFile("config.toml");
    final BufferedWriter fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8);

    fileWriter.write("an-invalid-syntax=======....");
    fileWriter.flush();

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

    assertThatThrownBy(
            () ->
                providerUnderTest.defaultValue(
                    OptionSpec.builder("an-option").type(String.class).build()))
        .isInstanceOf(ParameterException.class)
        .hasMessage(
            "Invalid TOML configuration: org.apache.tuweni.toml.TomlParseError: Unexpected '=', expected ', \", ''', "
                + "\"\"\", a number, a boolean, a date/time, an array, or a table (line 1, column 19)");
  }

  @Test
  public void unknownOptionMustThrow() throws IOException {

    when(mockCommandLine.getCommandSpec()).thenReturn(mockCommandSpec);
    Map<String, OptionSpec> validOptionsMap = new HashMap<>();
    when(mockCommandSpec.optionsMap()).thenReturn(validOptionsMap);

    final File tempConfigFile = temp.newFile("config.toml");
    final BufferedWriter fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8);

    fileWriter.write("invalid_option=true");
    fileWriter.flush();

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

    assertThatThrownBy(
            () ->
                providerUnderTest.defaultValue(
                    OptionSpec.builder("an-option").type(String.class).build()))
        .isInstanceOf(ParameterException.class)
        .hasMessage("Unknown option in TOML configuration file: invalid_option");
  }
}
