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
package tech.pegasys.pantheon.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import picocli.CommandLine;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

@RunWith(MockitoJUnitRunner.class)
public class TomlConfigFileDefaultProviderTest {
  @Mock CommandLine mockCommandLine;

  @Rule public final TemporaryFolder temp = new TemporaryFolder();

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Test
  public void defaultValueIsNullIfNoMatchingKeyFoundOtherwiseTheValue() throws IOException {
    final File tempConfigFile = temp.newFile("config.toml");
    try (final Writer fileWriter = Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("an-option='123'");
      fileWriter.flush();

      final TomlConfigFileDefaultProvider providerUnderTest =
          new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

      // this option won't be found in config
      assertThat(providerUnderTest.defaultValue(OptionSpec.builder("myoption").build())).isNull();

      // this option must be found in config
      assertThat(providerUnderTest.defaultValue(OptionSpec.builder("an-option").build()))
          .isEqualTo("123");
    }
  }

  @Test
  public void defaultValueForOptionMustMatchType() throws IOException {
    final File tempConfigFile = temp.newFile("config.toml");
    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("a-boolean-option=true");
      fileWriter.newLine();
      fileWriter.write("a-multy-value-option=[\"value1\", \"value2\"]");
      fileWriter.newLine();
      fileWriter.write("an-int-value-option=123");
      fileWriter.newLine();
      fileWriter.write("an-string-value-option='my value'");
      fileWriter.flush();

      final TomlConfigFileDefaultProvider providerUnderTest =
          new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-boolean-option").type(Boolean.class).build()))
          .isEqualTo("true");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("a-multy-value-option").type(Collection.class).build()))
          .isEqualTo("value1,value2");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("an-int-value-option").type(Integer.class).build()))
          .isEqualTo("123");

      assertThat(
              providerUnderTest.defaultValue(
                  OptionSpec.builder("an-string-value-option").type(String.class).build()))
          .isEqualTo("my value");
    }
  }

  @Test
  public void configFileNotFoundMustThrow() {

    exceptionRule.expect(ParameterException.class);

    final File nonExistingFile = new File("doesnt.exit");
    exceptionRule.expectMessage("Unable to read TOML configuration, file not found.");

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, nonExistingFile);

    providerUnderTest.defaultValue(OptionSpec.builder("an-option").type(String.class).build());
  }

  @Test
  public void invalidConfigMustThrow() throws IOException {

    exceptionRule.expect(ParameterException.class);
    exceptionRule.expectMessage("Unable to read TOML configuration file");

    final File tempConfigFile = temp.newFile("config.toml");

    final TomlConfigFileDefaultProvider providerUnderTest =
        new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

    providerUnderTest.defaultValue(OptionSpec.builder("an-option").type(String.class).build());
  }

  @Test
  public void invalidConfigContentMustThrow() throws IOException {

    exceptionRule.expect(ParameterException.class);
    exceptionRule.expectMessage(
        "Invalid TOML configuration : Unexpected '=', expected ', \", ''', "
            + "\"\"\", a number, a boolean, a date/time, an array, or a table (line 1, column 19)");

    final File tempConfigFile = temp.newFile("config.toml");
    try (final BufferedWriter fileWriter =
        Files.newBufferedWriter(tempConfigFile.toPath(), UTF_8)) {

      fileWriter.write("an-invalid-syntax=======....");
      fileWriter.flush();

      final TomlConfigFileDefaultProvider providerUnderTest =
          new TomlConfigFileDefaultProvider(mockCommandLine, tempConfigFile);

      providerUnderTest.defaultValue(OptionSpec.builder("an-option").type(String.class).build());
    }
  }
}
