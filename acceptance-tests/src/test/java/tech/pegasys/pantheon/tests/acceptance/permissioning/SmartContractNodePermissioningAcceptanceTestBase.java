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
package tech.pegasys.pantheon.tests.acceptance.permissioning;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.perm.SmartContractNodePermissioningConditions;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.perm.SmartContractNodePermissioningTransactions;

import java.io.IOException;

class SmartContractNodePermissioningAcceptanceTestBase extends AcceptanceTestBase {

  private final SmartContractNodePermissioningTransactions smartContractNodePermissioning;
  private final SmartContractNodePermissioningConditions smartContractNodePermissioningConditions;

  private static final String CONTRACT_ADDRESS = "0x0000000000000000000000000000000000009999";
  private static final String GENESIS_FILE = "/permissioning/simple_permissioning_genesis.json";

  protected final Cluster permissionedCluster;

  protected SmartContractNodePermissioningAcceptanceTestBase() {
    super();
    smartContractNodePermissioning = new SmartContractNodePermissioningTransactions(accounts);
    smartContractNodePermissioningConditions =
        new SmartContractNodePermissioningConditions(smartContractNodePermissioning);
    this.permissionedCluster = permissionedCluster();
  }

  private Cluster permissionedCluster() {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().setAwaitPeerDiscovery(false).build();
    return new Cluster(clusterConfiguration, net);
  }

  protected Node permissionedNode(final String name) {
    return permissionedNodeBuilder
        .name(name)
        .genesisFile(GENESIS_FILE)
        .nodesContractEnabled(CONTRACT_ADDRESS)
        .build();
  }

  protected Node bootnode(final String name) {
    try {
      return pantheon.createCustomGenesisNode(name, GENESIS_FILE, true);
    } catch (IOException e) {
      throw new RuntimeException("Error creating node", e);
    }
  }

  protected Node node(final String name) {
    try {
      return pantheon.createCustomGenesisNode(name, GENESIS_FILE, false);
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
    return smartContractNodePermissioningConditions.nodeIsAllowed(CONTRACT_ADDRESS, node);
  }

  protected Transaction<Hash> forbidNode(final Node node) {
    return smartContractNodePermissioning.forbidNode(CONTRACT_ADDRESS, node);
  }

  protected Condition nodeIsForbidden(final Node node) {
    return smartContractNodePermissioningConditions.nodeIsForbidden(CONTRACT_ADDRESS, node);
  }

  protected Condition connectionIsAllowed(final Node source, final Node target) {
    return smartContractNodePermissioningConditions.connectionIsAllowed(
        CONTRACT_ADDRESS, source, target);
  }

  protected Condition connectionIsForbidden(final Node source, final Node target) {
    return smartContractNodePermissioningConditions.connectionIsForbidden(
        CONTRACT_ADDRESS, source, target);
  }
}
