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
package org.hyperledger.besu.tests.acceptance.permissioning;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.condition.perm.NodeSmartContractPermissioningConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.NodeSmartContractPermissioningTransactions;

import java.io.IOException;

class NodeSmartContractPermissioningAcceptanceTestBase extends AcceptanceTestBase {

  private final NodeSmartContractPermissioningTransactions smartContractNodePermissioning;
  private final NodeSmartContractPermissioningConditions nodeSmartContractPermissioningConditions;

  protected static final String CONTRACT_ADDRESS = "0x0000000000000000000000000000000000009999";
  protected static final String GENESIS_FILE = "/permissioning/simple_permissioning_genesis.json";

  protected final Cluster permissionedCluster;

  protected NodeSmartContractPermissioningAcceptanceTestBase() {
    super();
    smartContractNodePermissioning = new NodeSmartContractPermissioningTransactions(accounts);
    nodeSmartContractPermissioningConditions =
        new NodeSmartContractPermissioningConditions(smartContractNodePermissioning);
    this.permissionedCluster = permissionedCluster();
  }

  private Cluster permissionedCluster() {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    return new Cluster(clusterConfiguration, net);
  }

  protected Node permissionedNode(final String name, final Node... localConfigAllowedNodes) {
    return permissionedNode(name, GENESIS_FILE, localConfigAllowedNodes);
  }

  protected Node permissionedNode(
      final String name, final String genesisFile, final Node... localConfigAllowedNodes) {
    PermissionedNodeBuilder permissionedNodeBuilder =
        this.permissionedNodeBuilder
            .name(name)
            .genesisFile(genesisFile)
            .nodesContractEnabled(CONTRACT_ADDRESS);
    if (localConfigAllowedNodes != null && localConfigAllowedNodes.length > 0) {
      permissionedNodeBuilder.nodesPermittedInConfig(localConfigAllowedNodes);
    }
    return permissionedNodeBuilder.build();
  }

  protected Node bootnode(final String name) {
    return bootnode(name, GENESIS_FILE);
  }

  protected Node bootnode(final String name, final String genesisFile) {
    try {
      return besu.createCustomGenesisNode(name, genesisFile, true);
    } catch (IOException e) {
      throw new RuntimeException("Error creating node", e);
    }
  }

  protected Node node(final String name) {
    try {
      return besu.createCustomGenesisNode(name, GENESIS_FILE, false);
    } catch (IOException e) {
      throw new RuntimeException("Error creating node", e);
    }
  }

  protected Node miner(final String name) {
    try {
      return besu.createCustomGenesisNode(name, GENESIS_FILE, false, true);
    } catch (IOException e) {
      throw new RuntimeException("Error creating node", e);
    }
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    permissionedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }

  protected Transaction<Hash> allowNode(final Node node) {
    return smartContractNodePermissioning.allowNode(CONTRACT_ADDRESS, node);
  }

  protected Condition nodeIsAllowed(final Node node) {
    return nodeSmartContractPermissioningConditions.nodeIsAllowed(CONTRACT_ADDRESS, node);
  }

  protected Transaction<Hash> forbidNode(final Node node) {
    return smartContractNodePermissioning.forbidNode(CONTRACT_ADDRESS, node);
  }

  protected Condition nodeIsForbidden(final Node node) {
    return nodeSmartContractPermissioningConditions.nodeIsForbidden(CONTRACT_ADDRESS, node);
  }

  protected Condition connectionIsAllowed(final Node source, final Node target) {
    return nodeSmartContractPermissioningConditions.connectionIsAllowed(
        CONTRACT_ADDRESS, source, target);
  }

  protected Condition connectionIsForbidden(final Node source, final Node target) {
    return nodeSmartContractPermissioningConditions.connectionIsForbidden(
        CONTRACT_ADDRESS, source, target);
  }
}
