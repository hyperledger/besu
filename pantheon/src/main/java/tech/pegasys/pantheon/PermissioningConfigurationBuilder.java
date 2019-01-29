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

import static java.nio.charset.StandardCharsets.UTF_8;

import tech.pegasys.pantheon.cli.custom.EnodeToURIPropertyConverter;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import net.consensys.cava.toml.TomlArray;
import net.consensys.cava.toml.TomlParseResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PermissioningConfigurationBuilder {

  private static final Logger LOG = LogManager.getLogger();

  // will be used to reload the config from a file while node is running
  public static PermissioningConfiguration permissioningConfigurationFromToml(
      final String configFilePath,
      final boolean permissionedNodeEnabled,
      final boolean permissionedAccountEnabled) {

    String permToml = permissioningConfigTomlAsString(permissioningConfigFile(configFilePath));
    return permissioningConfiguration(
        TomlConfigFileParser.loadConfiguration(permToml),
        permissionedNodeEnabled,
        permissionedAccountEnabled);
  }

  public static PermissioningConfiguration permissioningConfiguration(
      final TomlParseResult permToml,
      final boolean permissionedNodeEnabled,
      final boolean permissionedAccountEnabled) {

    TomlArray accountWhitelistTomlArray = permToml.getArray("accounts-whitelist");
    TomlArray nodeWhitelistTomlArray = permToml.getArray("nodes-whitelist");

    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    if (permissionedAccountEnabled) {
      if (accountWhitelistTomlArray != null) {
        List<String> accountsWhitelistToml =
            accountWhitelistTomlArray
                .toList()
                .stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        permissioningConfiguration.setAccountWhitelist(accountsWhitelistToml);
      } else {
        permissioningConfiguration.setAccountWhitelist(new ArrayList<>());
      }
    }
    if (permissionedNodeEnabled) {
      if (nodeWhitelistTomlArray != null) {
        List<URI> nodesWhitelistToml =
            nodeWhitelistTomlArray
                .toList()
                .stream()
                .map(Object::toString)
                .map(EnodeToURIPropertyConverter::convertToURI)
                .collect(Collectors.toList());
        permissioningConfiguration.setNodeWhitelist(nodesWhitelistToml);
      } else {
        permissioningConfiguration.setNodeWhitelist(new ArrayList<>());
      }
    }
    return permissioningConfiguration;
  }

  private static String permissioningConfigTomlAsString(final File file) {
    try {
      return Resources.toString(file.toURI().toURL(), UTF_8);
    } catch (final Exception e) {
      LOG.error("Unable to load permissioning config {}.", file, e);
      return null;
    }
  }

  private static File permissioningConfigFile(final String filename) {

    final File permissioningConfigFile = new File(filename);
    if (permissioningConfigFile.exists()) {
      return permissioningConfigFile;
    } else {
      LOG.error("File does not exist: permissioning config path: {}.", filename);
      return null;
    }
  }
}
