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
package tech.pegasys.pantheon.cli.util;

import tech.pegasys.pantheon.ethereum.core.Wei;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseError;
import net.consensys.cava.toml.TomlParseResult;
import picocli.CommandLine;
import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;

public class TomlConfigFileDefaultProvider implements IDefaultValueProvider {

  private final CommandLine commandLine;
  private final File configFile;
  private TomlParseResult result;

  public TomlConfigFileDefaultProvider(final CommandLine commandLine, final File configFile) {
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

  private String getConfigurationValue(final OptionSpec optionSpec) {
    final String defaultValue;
    // Convert config values to the right string representation for default string value
    if (optionSpec.type().equals(Boolean.class)) {
      defaultValue = getBooleanEntryAsString(optionSpec);
    } else if (optionSpec.isMultiValue()) {
      defaultValue = getListEntryAsString(optionSpec);
    } else if (optionSpec.type().equals(Integer.class)) {
      defaultValue = getIntegerEntryAsString(optionSpec);
    } else if (optionSpec.type().equals(Wei.class)) {
      defaultValue = getIntegerEntryAsString(optionSpec);
    } else if (optionSpec.type().equals(BigInteger.class)) {
      defaultValue = getIntegerEntryAsString(optionSpec);
    } else { // else will be treated as String
      defaultValue = getEntryAsString(optionSpec);
    }
    return defaultValue;
  }

  private String getEntryAsString(final OptionSpec spec) {
    // returns the string value of the config line corresponding to the option in toml file
    // or null if not present in the config
    return getKeyName(spec).map(result::getString).orElse(null);
  }

  private Optional<String> getKeyName(final OptionSpec spec) {
    // If any of the names of the option are used as key in the toml results
    // then returns the value of first one.
    return Arrays.stream(spec.names())
        // remove leading dashes on option name as we can have "--" or "-" options
        .map(name -> name.replaceFirst("^-+", ""))
        .filter(result::contains)
        .findFirst();
  }

  private String getListEntryAsString(final OptionSpec spec) {
    // returns the string representation of the array value of the config line in CLI format
    // corresponding to the option in toml file
    // or null if not present in the config
    return getKeyName(spec)
        .map(result::getArray)
        .map(
            tomlArray ->
                tomlArray.toList().stream().map(Object::toString).collect(Collectors.joining(",")))
        .orElse(null);
  }

  private String getBooleanEntryAsString(final OptionSpec spec) {
    // return the string representation of the boolean value corresponding to the option in toml
    // file
    // or null if not present in the config
    return getKeyName(spec).map(result::getBoolean).map(Object::toString).orElse(null);
  }

  private String getIntegerEntryAsString(final OptionSpec spec) {
    // return the string representation of the integer value corresponding to the option in toml
    // file
    // or null if not present in the config
    return getKeyName(spec).map(result::get).map(String::valueOf).orElse(null);
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
              result.errors().stream()
                  .map(TomlParseError::toString)
                  .collect(Collectors.joining("%n"));

          throw new ParameterException(
              commandLine, String.format("Invalid TOML configuration: %s", errors));
        }

        checkUnknownOptions(result);

        this.result = result;

      } catch (final IOException e) {
        throw new ParameterException(
            commandLine, "Unable to read TOML configuration, file not found.");
      }
    }

    checkConfigurationValidity();
  }

  private void checkUnknownOptions(final TomlParseResult result) {
    final CommandSpec commandSpec = commandLine.getCommandSpec();

    final Set<String> unknownOptionsList =
        result.keySet().stream()
            .filter(option -> !commandSpec.optionsMap().containsKey("--" + option))
            .collect(Collectors.toSet());

    if (!unknownOptionsList.isEmpty()) {
      final String options = unknownOptionsList.size() > 1 ? "options" : "option";
      final String csvUnknownOptions =
          unknownOptionsList.stream().collect(Collectors.joining(", "));
      throw new ParameterException(
          commandLine,
          String.format("Unknown %s in TOML configuration file: %s", options, csvUnknownOptions));
    }
  }
}
