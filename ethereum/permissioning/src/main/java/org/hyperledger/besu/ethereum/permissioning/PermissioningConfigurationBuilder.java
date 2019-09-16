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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import net.consensys.cava.toml.TomlArray;
import net.consensys.cava.toml.TomlParseResult;

public class PermissioningConfigurationBuilder {

  public static final String ACCOUNTS_WHITELIST_KEY = "accounts-whitelist";
  public static final String NODES_WHITELIST_KEY = "nodes-whitelist";

  public static SmartContractPermissioningConfiguration smartContractPermissioningConfiguration(
      final Address address, final boolean smartContractPermissionedNodeEnabled) {
    SmartContractPermissioningConfiguration config = new SmartContractPermissioningConfiguration();
    config.setNodeSmartContractAddress(address);
    config.setSmartContractNodeWhitelistEnabled(smartContractPermissionedNodeEnabled);
    return config;
  }

  public static LocalPermissioningConfiguration permissioningConfiguration(
      final boolean nodePermissioningEnabled,
      final String nodePermissioningConfigFilepath,
      final boolean accountPermissioningEnabled,
      final String accountPermissioningConfigFilepath)
      throws Exception {

    final LocalPermissioningConfiguration permissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();

    loadNodePermissioning(
        permissioningConfiguration, nodePermissioningEnabled, nodePermissioningConfigFilepath);
    loadAccountPermissioning(
        permissioningConfiguration,
        accountPermissioningEnabled,
        accountPermissioningConfigFilepath);

    return permissioningConfiguration;
  }

  private static LocalPermissioningConfiguration loadNodePermissioning(
      final LocalPermissioningConfiguration permissioningConfiguration,
      final boolean localConfigNodePermissioningEnabled,
      final String nodePermissioningConfigFilepath)
      throws Exception {

    if (localConfigNodePermissioningEnabled) {
      final TomlParseResult nodePermissioningToml = readToml(nodePermissioningConfigFilepath);
      final TomlArray nodeWhitelistTomlArray = nodePermissioningToml.getArray(NODES_WHITELIST_KEY);

      permissioningConfiguration.setNodePermissioningConfigFilePath(
          nodePermissioningConfigFilepath);

      if (nodeWhitelistTomlArray != null) {
        List<URI> nodesWhitelistToml =
            nodeWhitelistTomlArray
                .toList()
                .parallelStream()
                .map(Object::toString)
                .map(EnodeURL::asURI)
                .collect(Collectors.toList());
        permissioningConfiguration.setNodeWhitelist(nodesWhitelistToml);
      } else {
        throw new Exception(
            NODES_WHITELIST_KEY
                + " config option missing in TOML config file "
                + nodePermissioningConfigFilepath);
      }
    }
    return permissioningConfiguration;
  }

  private static LocalPermissioningConfiguration loadAccountPermissioning(
      final LocalPermissioningConfiguration permissioningConfiguration,
      final boolean localConfigAccountPermissioningEnabled,
      final String accountPermissioningConfigFilepath)
      throws Exception {

    if (localConfigAccountPermissioningEnabled) {
      final TomlParseResult accountPermissioningToml = readToml(accountPermissioningConfigFilepath);
      final TomlArray accountWhitelistTomlArray =
          accountPermissioningToml.getArray(ACCOUNTS_WHITELIST_KEY);

      permissioningConfiguration.setAccountPermissioningConfigFilePath(
          accountPermissioningConfigFilepath);

      if (accountWhitelistTomlArray != null) {
        List<String> accountsWhitelistToml =
            accountWhitelistTomlArray
                .toList()
                .parallelStream()
                .map(Object::toString)
                .collect(Collectors.toList());

        accountsWhitelistToml.stream()
            .filter(s -> !AccountLocalConfigPermissioningController.isValidAccountString(s))
            .findFirst()
            .ifPresent(
                s -> {
                  throw new IllegalArgumentException("Invalid account " + s);
                });

        permissioningConfiguration.setAccountWhitelist(accountsWhitelistToml);
      } else {
        throw new Exception(
            ACCOUNTS_WHITELIST_KEY
                + " config option missing in TOML config file "
                + accountPermissioningConfigFilepath);
      }
    }

    return permissioningConfiguration;
  }

  private static TomlParseResult readToml(final String filepath) throws Exception {
    TomlParseResult toml;

    try {
      toml = TomlConfigFileParser.loadConfigurationFromFile(filepath);
    } catch (Exception e) {
      throw new Exception(
          "Unable to read permissioning TOML config file : " + filepath + " " + e.getMessage());
    }
    return toml;
  }
}
