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
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURLImpl;
import org.hyperledger.besu.ethereum.permissioning.node.NodePermissioningController;
import org.hyperledger.besu.ethereum.permissioning.node.provider.SyncStatusNodePermissioningProvider;
import org.hyperledger.besu.ethereum.transaction.TransactionSimulator;
import org.hyperledger.besu.plugin.data.EnodeURL;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodePermissioningControllerFactory {

  private static final Logger LOG =
      LoggerFactory.getLogger(NodePermissioningControllerFactory.class);

  public NodePermissioningController create(
      final PermissioningConfiguration permissioningConfiguration,
      final Synchronizer synchronizer,
      final Collection<EnodeURL> fixedNodes,
      final Bytes localNodeId,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem,
      final Blockchain blockchain,
      final List<NodeConnectionPermissioningProvider> pluginProviders) {

    ArrayList<NodeConnectionPermissioningProvider> providers = Lists.newArrayList(pluginProviders);

    final Optional<SyncStatusNodePermissioningProvider> syncStatusProviderOptional;

    if (permissioningConfiguration.getLocalConfig().isPresent()) {
      LocalPermissioningConfiguration localPermissioningConfiguration =
          permissioningConfiguration.getLocalConfig().get();
      if (localPermissioningConfiguration.isNodeAllowlistEnabled()) {
        NodeLocalConfigPermissioningController localProvider =
            new NodeLocalConfigPermissioningController(
                localPermissioningConfiguration,
                new ArrayList<>(fixedNodes),
                localNodeId,
                metricsSystem);
        providers.add(localProvider);
      }
    }

    if (permissioningConfiguration.getSmartContractConfig().isPresent()
        && permissioningConfiguration
            .getSmartContractConfig()
            .get()
            .isSmartContractNodeAllowlistEnabled()) {

      configureNodePermissioningSmartContractProvider(
          permissioningConfiguration, transactionSimulator, metricsSystem, providers);

      if (fixedNodes.isEmpty()) {
        syncStatusProviderOptional = Optional.empty();
      } else {
        syncStatusProviderOptional =
            Optional.of(
                new SyncStatusNodePermissioningProvider(synchronizer, fixedNodes, metricsSystem));
      }
    } else {
      syncStatusProviderOptional = Optional.empty();
    }

    final NodePermissioningController nodePermissioningController =
        new NodePermissioningController(syncStatusProviderOptional, providers);

    permissioningConfiguration
        .getSmartContractConfig()
        .ifPresent(
            config -> {
              if (config.isSmartContractNodeAllowlistEnabled()) {
                validatePermissioningContract(
                    nodePermissioningController,
                    permissioningConfiguration.getSmartContractConfig().get());
              }
            });

    return nodePermissioningController;
  }

  private void configureNodePermissioningSmartContractProvider(
      final PermissioningConfiguration permissioningConfiguration,
      final TransactionSimulator transactionSimulator,
      final MetricsSystem metricsSystem,
      final List<NodeConnectionPermissioningProvider> providers) {
    final SmartContractPermissioningConfiguration smartContractPermissioningConfig =
        permissioningConfiguration.getSmartContractConfig().get();
    final Address nodePermissioningSmartContractAddress =
        smartContractPermissioningConfig.getNodeSmartContractAddress();

    final NodeConnectionPermissioningProvider smartContractProvider;
    switch (smartContractPermissioningConfig.getNodeSmartContractInterfaceVersion()) {
      case 1:
        {
          smartContractProvider =
              new NodeSmartContractPermissioningController(
                  nodePermissioningSmartContractAddress, transactionSimulator, metricsSystem);
          break;
        }
      case 2:
        {
          smartContractProvider =
              new NodeSmartContractV2PermissioningController(
                  nodePermissioningSmartContractAddress, transactionSimulator, metricsSystem);
          break;
        }
      default:
        throw new IllegalStateException(
            "Invalid node Smart contract permissioning interface version");
    }
    providers.add(smartContractProvider);
  }

  private void validatePermissioningContract(
      final NodePermissioningController nodePermissioningController,
      final SmartContractPermissioningConfiguration smartContractPermissioningConfig) {
    LOG.debug("Validating onchain node permissioning smart contract configuration");

    // eliminate the sync status and other checks, so we can just check the smart contract function
    final NodePermissioningController tempControllerCheckingSmartContractOnly =
        new NodePermissioningController(
            Optional.empty(), nodePermissioningController.getProviders());

    try {
      // the enodeURLs don't matter. We just want to check if a call to the smart contract succeeds
      tempControllerCheckingSmartContractOnly.isPermitted(
          EnodeURLImpl.fromString(
              "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@10.3.58.6:30303"),
          EnodeURLImpl.fromString(
              "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@10.3.58.6:30303"));
      LOG.debug(
          "Successful validation of onchain node permissioning smart contract configuration!");
    } catch (Exception e) {
      final String msg =
          String.format(
              "Error: node permissioning contract at address %s does not match the expected interface version %s.",
              smartContractPermissioningConfig.getNodeSmartContractAddress(),
              smartContractPermissioningConfig.getNodeSmartContractInterfaceVersion());
      throw new IllegalStateException(msg, e);
    }
  }
}
