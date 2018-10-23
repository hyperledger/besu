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

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlArray;
import net.consensys.cava.toml.TomlParseError;
import net.consensys.cava.toml.TomlParseResult;
import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

public class TomlConfigFileDefaultProvider implements IDefaultValueProvider {

  private final CommandLine commandLine;
  private final File configFile;
  private TomlParseResult result;

  TomlConfigFileDefaultProvider(final CommandLine commandLine, final File configFile) {
    this.commandLine = commandLine;
    this.configFile = configFile;
  }

  @Override
  public String defaultValue(final ArgSpec argSpec) {
    loadConfigurationFromFile();

    // only options can be used in config because a name is needed for the key
    // so we skip default for positional params
    return argSpec.isOption() ? getConfigurationValue(((OptionSpec) argSpec)) : null;
  }

  private String getConfigurationKey(final OptionSpec optionSpec) {
    // remove leading dashes on option name as we can have "--" or "-" options
    return optionSpec.longestName().replaceFirst("^-+", "");
  }

  private String getConfigurationValue(final OptionSpec optionSpec) {
    final String optionKey = getConfigurationKey(optionSpec);
    final String
        defaultValue; // Convert values to the right string representation for default string value
    if (optionSpec.type().equals(Boolean.class)) {
      defaultValue = getBooleanEntryAsString(optionKey);
    } else if (optionSpec.isMultiValue()) {
      defaultValue = getListEntryAsString(optionKey);
    } else if (optionSpec.type().equals(Integer.class)) {
      defaultValue = getIntegerEntryAsString(optionKey);
    } else { // else will be treated as String
      defaultValue = getEntryAsString(optionKey);
    }
    return defaultValue;
  }

  private String getEntryAsString(final String optionKey) {
    return result.getString(optionKey);
  }

  private String getListEntryAsString(final String optionKey) {
    final TomlArray tomlArray = result.getArray(optionKey);
    if (tomlArray != null) {
      final List<String> items =
          tomlArray.toList().stream().map(e -> (String) e).collect(Collectors.toList());
      return String.join(",", items);
    }
    return null;
  }

  private String getBooleanEntryAsString(final String optionKey) {
    final Boolean booleanValue = result.getBoolean(optionKey);
    if (booleanValue != null) {
      return !booleanValue ? "false" : "true";
    }
    return null;
  }

  private String getIntegerEntryAsString(final String optionKey) {
    if (result.get(optionKey) != null) {
      return String.valueOf(result.get(optionKey));
    }
    return null;
  }

  private void checkConfigurationValidity() {
    if (result == null || result.isEmpty())
      throw new ParameterException(
          commandLine, String.format("Unable to read TOML configuration file %s", configFile));
  }

  private void loadConfigurationFromFile() {

    if (result == null) {
      try {
        final TomlParseResult result = Toml.parse(configFile.toPath());

        if (result.hasErrors()) {
          final String errors =
              result
                  .errors()
                  .stream()
                  .map(TomlParseError::toString)
                  .collect(Collectors.joining("%n"));
          ;
          throw new ParameterException(
              commandLine, String.format("Invalid TOML configuration : %s", errors));
        }

        this.result = result;

      } catch (final IOException e) {
        throw new ParameterException(
            commandLine, "Unable to read TOML configuration, file not found.");
      }
    }

    checkConfigurationValidity();
  }
}
