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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.cli.DefaultCommandValues
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis
import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfigurationBuilder
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration
import org.slf4j.Logger
import picocli.CommandLine
import java.nio.file.Path
import java.util.*

/** Handles configuration options for permissions in Besu.  */ // TODO: implement CLIOption<PermissioningConfiguration>
class PermissionsOptions
/** Default constructor.  */
{
    @CommandLine.Option(
        names = ["--permissions-nodes-config-file-enabled"],
        description = ["Enable node level permissions (default: \${DEFAULT-VALUE})"]
    )
    private var permissionsNodesEnabled = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--permissions-nodes-config-file"],
        description = ["Node permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)"]
    )
    private var nodePermissionsConfigFile: String? = null

    @CommandLine.Option(
        names = ["--permissions-accounts-config-file-enabled"],
        description = ["Enable account level permissions (default: \${DEFAULT-VALUE})"]
    )
    private var permissionsAccountsEnabled = false

    // PicoCLI requires non-final Strings.
    @CommandLine.Option(
        names = ["--permissions-accounts-config-file"],
        description = ["Account permissioning config TOML file (default: a file named \"permissions_config.toml\" in the Besu data folder)"]
    )
    private var accountPermissionsConfigFile: String? = null

    @CommandLine.Option(
        names = ["--permissions-nodes-contract-address"],
        description = [DEPRECATION_PREFIX + "Address of the node permissioning smart contract"],
        arity = "1"
    )
    private var permissionsNodesContractAddress: Address? = null

    @CommandLine.Option(
        names = ["--permissions-nodes-contract-version"], description = [(DEPRECATION_PREFIX
                + "Version of the EEA Node Permissioning interface (default: \${DEFAULT-VALUE})")]
    )
    private var permissionsNodesContractVersion = 1

    @CommandLine.Option(
        names = ["--permissions-nodes-contract-enabled"], description = [(DEPRECATION_PREFIX
                + "Enable node level permissions via smart contract (default: \${DEFAULT-VALUE})")]
    )
    private var permissionsNodesContractEnabled = false

    @CommandLine.Option(
        names = ["--permissions-accounts-contract-address"],
        description = [DEPRECATION_PREFIX + "Address of the account permissioning smart contract"],
        arity = "1"
    )
    private var permissionsAccountsContractAddress: Address? = null

    @CommandLine.Option(
        names = ["--permissions-accounts-contract-enabled"], description = [(DEPRECATION_PREFIX
                + "Enable account level permissions via smart contract (default: \${DEFAULT-VALUE})")]
    )
    private var permissionsAccountsContractEnabled = false

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
    @Throws(Exception::class)
    fun permissioningConfiguration(
        jsonRpcHttpOptions: JsonRpcHttpOptions,
        rpcWebsocketOptions: RpcWebsocketOptions,
        enodeDnsConfiguration: EnodeDnsConfiguration?,
        dataPath: Path,
        logger: Logger,
        commandLine: CommandLine
    ): Optional<PermissioningConfiguration> {
        if (!(localPermissionsEnabled() || contractPermissionsEnabled())) {
            if (jsonRpcHttpOptions.rpcHttpApis.contains(RpcApis.PERM.name)
                || rpcWebsocketOptions.rpcWsApis.contains(RpcApis.PERM.name)
            ) {
                logger.warn(
                    "Permissions are disabled. Cannot enable PERM APIs when not using Permissions."
                )
            }
            return Optional.empty()
        }

        val localPermissioningConfigurationOptional: Optional<LocalPermissioningConfiguration>
        if (localPermissionsEnabled()) {
            val nodePermissioningConfigFile =
                Optional.ofNullable(nodePermissionsConfigFile)
            val accountPermissioningConfigFile =
                Optional.ofNullable(accountPermissionsConfigFile)

            val localPermissioningConfiguration =
                PermissioningConfigurationBuilder.permissioningConfiguration(
                    permissionsNodesEnabled,
                    enodeDnsConfiguration,
                    nodePermissioningConfigFile.orElse(getDefaultPermissioningFilePath(dataPath)),
                    permissionsAccountsEnabled,
                    accountPermissioningConfigFile.orElse(getDefaultPermissioningFilePath(dataPath))
                )

            localPermissioningConfigurationOptional = Optional.of(localPermissioningConfiguration)
        } else {
            if (nodePermissionsConfigFile != null && !permissionsNodesEnabled) {
                logger.warn(
                    "Node permissioning config file set {} but no permissions enabled",
                    nodePermissionsConfigFile
                )
            }

            if (accountPermissionsConfigFile != null && !permissionsAccountsEnabled) {
                logger.warn(
                    "Account permissioning config file set {} but no permissions enabled",
                    accountPermissionsConfigFile
                )
            }
            localPermissioningConfigurationOptional = Optional.empty()
        }

        val smartContractPermissioningConfiguration =
            SmartContractPermissioningConfiguration.createDefault()

        if (java.lang.Boolean.TRUE == permissionsNodesContractEnabled) {
            logger.warn("Onchain (contract) node permissioning options are " + DEPRECATION_PREFIX)
            if (permissionsNodesContractAddress == null) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "No node permissioning contract address specified. Cannot enable smart contract based node permissioning."
                )
            } else {
                smartContractPermissioningConfiguration.isSmartContractNodeAllowlistEnabled =
                    permissionsNodesContractEnabled
                smartContractPermissioningConfiguration.nodeSmartContractAddress = permissionsNodesContractAddress
                smartContractPermissioningConfiguration.nodeSmartContractInterfaceVersion =
                    permissionsNodesContractVersion
            }
        } else if (permissionsNodesContractAddress != null) {
            logger.warn(
                "Node permissioning smart contract address set {} but smart contract node permissioning is disabled.",
                permissionsNodesContractAddress
            )
        }

        if (java.lang.Boolean.TRUE == permissionsAccountsContractEnabled) {
            logger.warn("Onchain (contract) account permissioning options are " + DEPRECATION_PREFIX)
            if (permissionsAccountsContractAddress == null) {
                throw CommandLine.ParameterException(
                    commandLine,
                    "No account permissioning contract address specified. Cannot enable smart contract based account permissioning."
                )
            } else {
                smartContractPermissioningConfiguration.isSmartContractAccountAllowlistEnabled =
                    permissionsAccountsContractEnabled
                smartContractPermissioningConfiguration.accountSmartContractAddress = permissionsAccountsContractAddress
            }
        } else if (permissionsAccountsContractAddress != null) {
            logger.warn(
                "Account permissioning smart contract address set {} but smart contract account permissioning is disabled.",
                permissionsAccountsContractAddress
            )
        }

        val permissioningConfiguration =
            PermissioningConfiguration(
                localPermissioningConfigurationOptional,
                Optional.of(smartContractPermissioningConfiguration)
            )

        return Optional.of(permissioningConfiguration)
    }

    private fun localPermissionsEnabled(): Boolean {
        return permissionsAccountsEnabled || permissionsNodesEnabled
    }

    private fun contractPermissionsEnabled(): Boolean {
        return permissionsNodesContractEnabled || permissionsAccountsContractEnabled
    }

    private fun getDefaultPermissioningFilePath(dataPath: Path): String {
        return (dataPath
            .toString() + System.getProperty("file.separator")
                + DefaultCommandValues.PERMISSIONING_CONFIG_LOCATION)
    }

    companion object {
        private const val DEPRECATION_PREFIX =
            "Deprecated. Onchain permissioning is deprecated. See CHANGELOG for alternative options. "
    }
}
