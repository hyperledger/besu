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
package org.hyperledger.besu.ethereum.permissioning;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import net.consensys.cava.toml.Toml;
import net.consensys.cava.toml.TomlParseError;
import net.consensys.cava.toml.TomlParseResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TomlConfigFileParser {

  protected static final Logger LOG = LogManager.getLogger();

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
          result.errors().stream().map(TomlParseError::toString).collect(Collectors.joining("\n"));
      throw new Exception("Invalid TOML configuration: \n" + errors);
    }

    return checkConfigurationValidity(result, toml);
  }

  public static TomlParseResult loadConfigurationFromFile(final String configFilePath)
      throws Exception {
    return loadConfiguration(configTomlAsString(tomlConfigFile(configFilePath)));
  }

  private static String configTomlAsString(final File file) throws Exception {
    return Resources.toString(file.toURI().toURL(), UTF_8);
  }

  private static File tomlConfigFile(final String filename) throws Exception {
    final File tomlConfigFile = new File(filename);
    if (tomlConfigFile.exists()) {
      if (!tomlConfigFile.canRead()) {
        throw new Exception(String.format("Read access denied for file at: %s", filename));
      }
      if (!tomlConfigFile.canWrite()) {
        LOG.warn(
            "Write access denied for file at: %s. Configuration modification operations will not be permitted.",
            filename);
      }
      return tomlConfigFile;
    } else {
      throw new FileNotFoundException(
          String.format("Configuration file does not exist: %s", filename));
    }
  }
}
