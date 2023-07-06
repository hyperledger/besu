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
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.condition.perm.AccountSmartContractPermissioningConditions;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.permissioning.PermissionedNodeBuilder;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.perm.AccountSmartContractPermissioningTransactions;

import java.io.IOException;
import java.util.List;

class AccountSmartContractPermissioningAcceptanceTestBase extends AcceptanceTestBase {

  private final AccountSmartContractPermissioningTransactions smartContractAccountPermissioning;
  private final AccountSmartContractPermissioningConditions
      accountSmartContractPermissioningConditions;

  private static final String CONTRACT_ADDRESS = "0x0000000000000000000000000000000000008888";
  private static final String GENESIS_FILE = "/permissioning/simple_permissioning_genesis.json";

  protected final Cluster permissionedCluster;

  protected AccountSmartContractPermissioningAcceptanceTestBase() {
    super();
    smartContractAccountPermissioning = new AccountSmartContractPermissioningTransactions(accounts);
    accountSmartContractPermissioningConditions =
        new AccountSmartContractPermissioningConditions(smartContractAccountPermissioning);
    this.permissionedCluster = permissionedCluster();
  }

  private Cluster permissionedCluster() {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    return new Cluster(clusterConfiguration, net);
  }

  protected Node permissionedNode(final String name, final List<String> accountsPermittedInConfig) {
    PermissionedNodeBuilder permissionedNodeBuilder =
        this.permissionedNodeBuilder
            .name(name)
            .genesisFile(GENESIS_FILE)
            .accountsContractEnabled(CONTRACT_ADDRESS);

    if (accountsPermittedInConfig != null && !accountsPermittedInConfig.isEmpty()) {
      permissionedNodeBuilder.accountsPermittedInConfig(accountsPermittedInConfig);
    }

    return permissionedNodeBuilder.build();
  }

  protected Node node(final String name) {
    try {
      return besu.createCustomGenesisNode(name, GENESIS_FILE, false);
    } catch (IOException e) {
      throw new RuntimeException("Error creating node", e);
    }
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    permissionedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }

  protected Transaction<Hash> allowAccount(final Account account) {
    return smartContractAccountPermissioning.allowAccount(CONTRACT_ADDRESS, account);
  }

  protected Transaction<Hash> forbidAccount(final Account account) {
    return smartContractAccountPermissioning.forbidAccount(CONTRACT_ADDRESS, account);
  }

  protected Condition accountIsAllowed(final Account account) {
    return accountSmartContractPermissioningConditions.accountIsAllowed(CONTRACT_ADDRESS, account);
  }

  protected Condition accountIsForbidden(final Account account) {
    return accountSmartContractPermissioningConditions.accountIsForbidden(
        CONTRACT_ADDRESS, account);
  }
}
