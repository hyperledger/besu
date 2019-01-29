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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TomlConfigFileParser {

  private static final Logger LOG = LogManager.getLogger();

  private static TomlParseResult checkConfigurationValidity(
      final TomlParseResult result, final String toml) {
    if (result == null || result.isEmpty()) {
      LOG.error("Unable to read TOML configuration file %s", toml);
    }
    return result;
  }

  public static TomlParseResult loadConfiguration(final String toml) {
    TomlParseResult result = Toml.parse(toml);

    if (result.hasErrors()) {
      final String errors =
          result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("%n"));
      ;
      LOG.error("Invalid TOML configuration : %s", errors);
    }

    return checkConfigurationValidity(result, toml);
  }
}
