/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.cli.DefaultCommandValues;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;

import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import picocli.CommandLine;

/** Handles configuration options for permissions in Besu. */
// TODO: implement CLIOption<PermissioningConfiguration>
public class PermissionsOptions {
  @CommandLine.Option(
      names = {"--permissions-nodes-config-file-enabled"},
      description = "Enable node level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--permissions-nodes-config-file"},
      description =
          "Node permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  private String nodePermissionsConfigFile = null;

  @CommandLine.Option(
      names = {"--permissions-accounts-config-file-enabled"},
      description = "Enable account level permissions (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @CommandLine.Option(
      names = {"--permissions-accounts-config-file"},
      description =
          "Account permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)")
  private String accountPermissionsConfigFile = null;

  private static final String DEPRECATION_PREFIX =
      "Deprecated. Onchain permissioning is deprecated. See CHANGELOG for alternative options. ";

  @CommandLine.Option(
      names = {"--permissions-nodes-contract-address"},
      description = DEPRECATION_PREFIX + "Address of the node permissioning smart contract",
      arity = "1")
  private final Address permissionsNodesContractAddress = null;

  @CommandLine.Option(
      names = {"--permissions-nodes-contract-version"},
      description =
          DEPRECATION_PREFIX
              + "Version of the EEA Node Permissioning interface (default: ${DEFAULT-VALUE})")
  private final Integer permissionsNodesContractVersion = 1;

  @CommandLine.Option(
      names = {"--permissions-nodes-contract-enabled"},
      description =
          DEPRECATION_PREFIX
              + "Enable node level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsNodesContractEnabled = false;

  @CommandLine.Option(
      names = {"--permissions-accounts-contract-address"},
      description = DEPRECATION_PREFIX + "Address of the account permissioning smart contract",
      arity = "1")
  private final Address permissionsAccountsContractAddress = null;

  @CommandLine.Option(
      names = {"--permissions-accounts-contract-enabled"},
      description =
          DEPRECATION_PREFIX
              + "Enable account level permissions via smart contract (default: ${DEFAULT-VALUE})")
  private final Boolean permissionsAccountsContractEnabled = false;

  /** Default constructor. */
  public PermissionsOptions() {}

  /**
   * Creates a PermissioningConfiguration based on the provided options.
   *
   * @param jsonRpcHttpOptions The JSON-RPC HTTP options
   * @param rpcWebsocketOptions The RPC websocket options
   * @param enodeDnsConfiguration The enode DNS configuration
   * @param dataPath The data path
   * @param logger The logger
   * @param commandLine The command line
   * @return An Optional PermissioningConfiguration instance
   * @throws Exception If an error occurs while creating the configuration
   */
  public Optional<PermissioningConfiguration> permissioningConfiguration(
      final JsonRpcHttpOptions jsonRpcHttpOptions,
      final RpcWebsocketOptions rpcWebsocketOptions,
      final EnodeDnsConfiguration enodeDnsConfiguration,
      final Path dataPath,
      final Logger logger,
      final CommandLine commandLine)
      throws Exception {
    if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
      if (jsonRpcHttpOptions.getRpcHttpApis().contains(RpcApis.PERM.name())
          || rpcWebsocketOptions.getRpcWsApis().contains(RpcApis.PERM.name())) {
        logger.warn(
            "Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");
      }
      return Optional.empty();
    }

    final Optional<LocalPermissioningConfiguration> localPermissioningConfigurationOptional;
    if (localPermissionsEnabled()) {
      final Optional<String> nodePermissioningConfigFile =
          Optional.ofNullable(nodePermissionsConfigFile);
      final Optional<String> accountPermissioningConfigFile =
          Optional.ofNullable(accountPermissionsConfigFile);

      final LocalPermissioningConfiguration localPermissioningConfiguration =
          PermissioningConfigurationBuilder.permissioningConfiguration(
              permissionsNodesEnabled,
              enodeDnsConfiguration,
              nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath(dataPath)),
              permissionsAccountsEnabled,
              accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath(dataPath)));

      localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration);
    } else {
      if (nodePermissionsConfigFile != null && !permissionsNodesEnabled) {
        logger.warn(
            "Node permissioning config file set {} but no permissions enabled",
            nodePermissionsConfigFile);
      }

      if (accountPermissionsConfigFile != null && !permissionsAccountsEnabled) {
        logger.warn(
            "Account permissioning config file set {} but no permissions enabled",
            accountPermissionsConfigFile);
      }
      localPermissioningConfigurationOptional = Optional.empty();
    }

    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        SmartContractPermissioningConfiguration.createDefault();

    if (Boolean.TRUE.equals(permissionsNodesContractEnabled)) {
      logger.warn("Onchain (contract) node permissioning options are " + DEPRECATION_PREFIX);
      if (permissionsNodesContractAddress == null) {
        throw new CommandLine.ParameterException(
            commandLine,
            "No node permissioning contract address specified. Cannot enable smart contract based node permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractNodeAllowlistEnabled(
            permissionsNodesContractEnabled);
        smartContractPermissioningConfiguration.setNodeSmartContractAddress(
            permissionsNodesContractAddress);
        smartContractPermissioningConfiguration.setNodeSmartContractInterfaceVersion(
            permissionsNodesContractVersion);
      }
    } else if (permissionsNodesContractAddress != null) {
      logger.warn(
          "Node permissioning smart contract address set {} but smart contract node permissioning is disabled.",
          permissionsNodesContractAddress);
    }

    if (Boolean.TRUE.equals(permissionsAccountsContractEnabled)) {
      logger.warn("Onchain (contract) account permissioning options are " + DEPRECATION_PREFIX);
      if (permissionsAccountsContractAddress == null) {
        throw new CommandLine.ParameterException(
            commandLine,
            "No account permissioning contract address specified. Cannot enable smart contract based account permissioning.");
      } else {
        smartContractPermissioningConfiguration.setSmartContractAccountAllowlistEnabled(
            permissionsAccountsContractEnabled);
        smartContractPermissioningConfiguration.setAccountSmartContractAddress(
            permissionsAccountsContractAddress);
      }
    } else if (permissionsAccountsContractAddress != null) {
      logger.warn(
          "Account permissioning smart contract address set {} but smart contract account permissioning is disabled.",
          permissionsAccountsContractAddress);
    }

    final PermissioningConfiguration permissioningConfiguration =
        new PermissioningConfiguration(
            localPermissioningConfigurationOptional,
            Optional.of(smartContractPermissioningConfiguration));

    return Optional.of(permissioningConfiguration);
  }

  private boolean localPermissionsEnabled() {
    return permissionsAccountsEnabled || permissionsNodesEnabled;
  }

  private boolean contractPermissionsEnabled() {
    return permissionsNodesContractEnabled || permissionsAccountsContractEnabled;
  }

  private String getDefaultPermissioningFilePath(final Path dataPath) {
    return dataPath
        + System.getProperty("file.separator")
        + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION;
  }
}
