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
package tech.pegasys.pantheon;

import java.util.stream.Collectors;

import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseError;
import net.consensys.cava.toml.TomlParseResult;

public class TomlConfigFileParser {

  private static TomlParseResult checkConfigurationValidity(
      final TomlParseResult result, final String toml) throws Exception {
    if (result == null || result.isEmpty()) {
      throw new Exception("Empty TOML result: " + toml);
    }
    return result;
  }

  public static TomlParseResult loadConfiguration(final String toml) throws Exception {
    TomlParseResult result = Toml.parse(toml);

    if (result.hasErrors()) {
      final String errors =
          result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("%n"));
      ;
      throw new Exception("Invalid TOML configuration : " + errors);
    }

    return checkConfigurationValidity(result, toml);
  }
}
