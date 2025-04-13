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
package org.hyperledger.besu.util

import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration
import org.hyperledger.besu.plugin.data.EnodeURL
import java.net.URI
import java.util.stream.Collectors

/** The Permissioning configuration validator.  */
object PermissioningConfigurationValidator {
    /**
     * Check if all nodes are in allowlist.
     *
     * @param nodeURIs the node URIs
     * @param permissioningConfiguration the permissioning configuration
     * @throws Exception In case of nodes are not in allow list
     */
    @Throws(Exception::class)
    @JvmStatic
    fun areAllNodesInAllowlist(
        nodeURIs: Collection<EnodeURL>?,
        permissioningConfiguration: LocalPermissioningConfiguration
    ) {
        if (permissioningConfiguration.isNodeAllowlistEnabled && nodeURIs != null) {
            val allowlistNodesWithoutQueryParam =
                permissioningConfiguration.nodeAllowlist.stream()
                    .map { obj: EnodeURL -> obj.toURIWithoutDiscoveryPort() }
                    .collect(Collectors.toList())

            val nodeURIsNotInAllowlist =
                nodeURIs.stream()
                    .map { obj: EnodeURL -> obj.toURIWithoutDiscoveryPort() }
                    .filter { uri: URI -> !allowlistNodesWithoutQueryParam.contains(uri) }
                    .collect(Collectors.toList())

            if (!nodeURIsNotInAllowlist.isEmpty()) {
                throw Exception(
                    "Specified node(s) not in nodes-allowlist " + enodesAsStrings(nodeURIsNotInAllowlist)
                )
            }
        }
    }

    private fun enodesAsStrings(enodes: List<URI>): Collection<String> {
        return enodes.parallelStream().map { obj: URI -> obj.toASCIIString() }.collect(Collectors.toList())
    }
}
