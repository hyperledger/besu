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
package org.hyperledger.besu.util;

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** The Permissioning configuration validator. */
public class PermissioningConfigurationValidator {
  /** Default Constructor. */
  PermissioningConfigurationValidator() {}

  /**
   * Check if all nodes are in allowlist.
   *
   * @param nodeURIs the node URIs
   * @param permissioningConfiguration the permissioning configuration
   * @throws Exception In case of nodes are not in allow list
   */
  public static void areAllNodesInAllowlist(
      final Collection<EnodeURL> nodeURIs,
      final LocalPermissioningConfiguration permissioningConfiguration)
      throws Exception {

    if (permissioningConfiguration.isNodeAllowlistEnabled() && nodeURIs != null) {
      final List<URI> allowlistNodesWithoutQueryParam =
          permissioningConfiguration.getNodeAllowlist().stream()
              .map(EnodeURL::toURIWithoutDiscoveryPort)
              .collect(toList());

      final List<URI> nodeURIsNotInAllowlist =
          nodeURIs.stream()
              .map(EnodeURL::toURIWithoutDiscoveryPort)
              .filter(uri -> !allowlistNodesWithoutQueryParam.contains(uri))
              .collect(toList());

      if (!nodeURIsNotInAllowlist.isEmpty()) {
        throw new Exception(
            "Specified node(s) not in nodes-allowlist " + enodesAsStrings(nodeURIsNotInAllowlist));
      }
    }
  }

  private static Collection<String> enodesAsStrings(final List<URI> enodes) {
    return enodes.parallelStream().map(URI::toASCIIString).collect(Collectors.toList());
  }
}
