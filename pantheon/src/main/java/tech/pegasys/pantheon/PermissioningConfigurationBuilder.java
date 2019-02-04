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
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.io.Resources;
import net.consensys.cava.toml.TomlArray;
import net.consensys.cava.toml.TomlParseResult;

public class PermissioningConfigurationBuilder {

  public static final String ACCOUNTS_WHITELIST = "accounts-whitelist";
  public static final String NODES_WHITELIST = "nodes-whitelist";

  // will be used to reload the config from a file while node is running
  public static PermissioningConfiguration permissioningConfigurationFromToml(
      final String configFilePath,
      final boolean permissionedNodeEnabled,
      final boolean permissionedAccountEnabled)
      throws Exception {

    return permissioningConfiguration(
        configFilePath, permissionedNodeEnabled, permissionedAccountEnabled);
  }

  public static PermissioningConfiguration permissioningConfiguration(
      final String configFilePath,
      final boolean permissionedNodeEnabled,
      final boolean permissionedAccountEnabled)
      throws Exception {

    TomlParseResult permToml;
    boolean foundValidOptions = false;

    try {
      permToml =
          TomlConfigFileParser.loadConfiguration(
              permissioningConfigTomlAsString(permissioningConfigFile(configFilePath)));
    } catch (Exception e) {
      throw new Exception(
          "Unable to read permissions TOML config file : " + configFilePath + " " + e.getMessage());
    }

    TomlArray accountWhitelistTomlArray = permToml.getArray(ACCOUNTS_WHITELIST);
    TomlArray nodeWhitelistTomlArray = permToml.getArray(NODES_WHITELIST);

    final PermissioningConfiguration permissioningConfiguration =
        PermissioningConfiguration.createDefault();
    permissioningConfiguration.setConfigurationFilePath(configFilePath);
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
        throw new Exception(
            ACCOUNTS_WHITELIST + " config option missing in TOML config file " + configFilePath);
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
        throw new Exception(
            NODES_WHITELIST + " config option missing in TOML config file " + configFilePath);
      }
    }
    return permissioningConfiguration;
  }

  private static String permissioningConfigTomlAsString(final File file) throws Exception {
    return Resources.toString(file.toURI().toURL(), UTF_8);
  }

  private static File permissioningConfigFile(final String filename) throws FileNotFoundException {

    final File permissioningConfigFile = new File(filename);
    if (permissioningConfigFile.exists()) {
      return permissioningConfigFile;
    } else {
      throw new FileNotFoundException(
          "File does not exist: permissioning config path: " + filename);
    }
  }
}
