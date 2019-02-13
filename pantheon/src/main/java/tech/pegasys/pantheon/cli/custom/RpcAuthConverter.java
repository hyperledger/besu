/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.cli.custom;

import tech.pegasys.pantheon.ethereum.permissioning.TomlConfigFileParser;

import java.io.IOException;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import net.consensys.cava.toml.TomlParseResult;
import picocli.CommandLine;

public class RpcAuthConverter implements CommandLine.ITypeConverter<String> {

  @Override
  public String convert(final String value) throws Exception {
    TomlParseResult tomlParseResult;
    try {
      tomlParseResult = TomlConfigFileParser.loadConfigurationFromFile(value);
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "An error occurred while opening the specified RPC authentication configuration file.");
    }

    if (tomlParseResult.hasErrors()) {
      throw new IllegalArgumentException(
          "An error occurred while parsing the specified RPC authentication configuration file.");
    }

    if (!verifyAllUsersHavePassword(tomlParseResult)) {
      throw new IllegalArgumentException("RPC user specified without password.");
    }

    if (!verifyAllEntriesHaveValues(tomlParseResult)) {
      throw new IllegalArgumentException(
          "RPC authentication configuration file contains invalid values.");
    }

    return value;
  }

  private boolean verifyAllUsersHavePassword(final TomlParseResult tomlParseResult) {
    int configuredUsers = tomlParseResult.getTable("Users").keySet().size();

    int usersWithPasswords =
        tomlParseResult
            .keyPathSet()
            .parallelStream()
            .filter(
                keySet ->
                    keySet.contains("Users")
                        && keySet.contains("password")
                        && !Strings.isNullOrEmpty(tomlParseResult.getString(keySet)))
            .collect(Collectors.toList())
            .size();

    return configuredUsers == usersWithPasswords;
  }

  private boolean verifyAllEntriesHaveValues(final TomlParseResult tomlParseResult) {
    return tomlParseResult
        .dottedKeySet()
        .parallelStream()
        .filter(keySet -> !keySet.contains("password"))
        .allMatch(dottedKey -> verifyArray(dottedKey, tomlParseResult));
  }

  private boolean verifyArray(final String key, final TomlParseResult tomlParseResult) {
    return tomlParseResult.isArray(key) && !tomlParseResult.getArrayOrEmpty(key).isEmpty();
  }
}
