/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.permissioning;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.tuweni.toml.TomlArray;
import org.apache.tuweni.toml.TomlParseResult;

public class PermissioningConfigurationBuilder {

  @Deprecated public static final String ACCOUNTS_WHITELIST_KEY = "accounts-whitelist";
  @Deprecated public static final String NODES_WHITELIST_KEY = "nodes-whitelist";
  public static final String ACCOUNTS_ALLOWLIST_KEY = "accounts-allowlist";
  public static final String NODES_ALLOWLIST_KEY = "nodes-allowlist";

  public static SmartContractPermissioningConfiguration smartContractPermissioningConfiguration(
      final Address address, final boolean smartContractPermissionedNodeEnabled) {
    SmartContractPermissioningConfiguration config = new SmartContractPermissioningConfiguration();
    config.setNodeSmartContractAddress(address);
    config.setSmartContractNodeAllowlistEnabled(smartContractPermissionedNodeEnabled);
    return config;
  }

  public static LocalPermissioningConfiguration permissioningConfiguration(
      final boolean nodePermissioningEnabled,
      final EnodeDnsConfiguration enodeDnsConfiguration,
      final String nodePermissioningConfigFilepath,
      final boolean accountPermissioningEnabled,
      final String accountPermissioningConfigFilepath)
      throws Exception {

    final LocalPermissioningConfiguration permissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();
    permissioningConfiguration.setEnodeDnsConfiguration(enodeDnsConfiguration);
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
      final TomlArray nodeAllowlistTomlArray =
          getAllowlistArray(nodePermissioningToml, NODES_ALLOWLIST_KEY, NODES_WHITELIST_KEY);

      permissioningConfiguration.setNodePermissioningConfigFilePath(
          nodePermissioningConfigFilepath);

      if (nodeAllowlistTomlArray != null) {
        List<EnodeURL> nodesAllowlistToml =
            nodeAllowlistTomlArray.toList().parallelStream()
                .map(Object::toString)
                .map(
                    url ->
                        EnodeURLImpl.fromString(
                            url, permissioningConfiguration.getEnodeDnsConfiguration()))
                .collect(Collectors.toList());
        permissioningConfiguration.setNodeAllowlist(nodesAllowlistToml);
      } else {
        throw new Exception(
            NODES_ALLOWLIST_KEY
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
      final TomlArray accountAllowlistTomlArray =
          getAllowlistArray(
              accountPermissioningToml, ACCOUNTS_ALLOWLIST_KEY, ACCOUNTS_WHITELIST_KEY);

      permissioningConfiguration.setAccountPermissioningConfigFilePath(
          accountPermissioningConfigFilepath);

      if (accountAllowlistTomlArray != null) {
        List<String> accountsAllowlistToml =
            accountAllowlistTomlArray.toList().parallelStream()
                .map(Object::toString)
                .collect(Collectors.toList());

        accountsAllowlistToml.stream()
            .filter(s -> !AccountLocalConfigPermissioningController.isValidAccountString(s))
            .findFirst()
            .ifPresent(
                s -> {
                  throw new IllegalArgumentException("Invalid account " + s);
                });

        permissioningConfiguration.setAccountAllowlist(accountsAllowlistToml);
      } else {
        throw new Exception(
            ACCOUNTS_ALLOWLIST_KEY
                + " config option missing in TOML config file "
                + accountPermissioningConfigFilepath);
      }
    }

    return permissioningConfiguration;
  }

  /**
   * This method allows support for both keys for now. Whitelist TOML keys will be removed in future
   * (breaking change)
   *
   * @param tomlParseResult result of a prior toml parse
   * @param primaryKey key to fetch
   * @param alternateKey alternate key to fetch
   * @return In order: the array of the primaryKey if it exists, or the array of the alternateKey if
   *     it exists, or null.
   */
  private static TomlArray getAllowlistArray(
      final TomlParseResult tomlParseResult, final String primaryKey, final String alternateKey) {
    final TomlArray array = tomlParseResult.getArray(primaryKey);
    if (array == null) {
      return tomlParseResult.getArray(alternateKey);
    } else {
      return array;
    }
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
