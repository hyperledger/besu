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

import org.hyperledger.besu.ethereum.permissioning.AllowlistPersistor;
import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccountLocalConfigPermissioningImportAcceptanceTest extends AcceptanceTestBase {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final String GENESIS_FILE = "/ibft/ibft.json";

  private Account sender;
  private Account beneficiary;
  private BesuNode bootnode;
  private BesuNode nodeA;
  private BesuNode nodeB;
  private Cluster permissionedCluster;

  @Before
  public void setUp() throws IOException {
    sender = accounts.getPrimaryBenefactor();
    beneficiary = accounts.createAccount("beneficiary");
    final List<String> allowList = List.of(sender.getAddress(), beneficiary.getAddress());
    final File sharedFile = folder.newFile();
    persistAllowList(allowList, sharedFile.toPath());
    bootnode = besu.createIbft2NonValidatorBootnode("bootnode", GENESIS_FILE);
    nodeA =
        besu.createIbft2NodeWithLocalAccountPermissioning(
            "nodeA", GENESIS_FILE, allowList, sharedFile);
    nodeB =
        besu.createIbft2NodeWithLocalAccountPermissioning(
            "nodeB", GENESIS_FILE, allowList, sharedFile);
    permissionedCluster =
        new Cluster(new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build(), net);

    permissionedCluster.start(bootnode, nodeA, nodeB);
  }

  @Test
  public void transactionFromDeniedAccountShouldNotBreakBlockImport() throws IOException {
    final File newPermissionsFile = folder.newFile();
    final List<String> allowList = List.of(beneficiary.getAddress());
    persistAllowList(allowList, newPermissionsFile.toPath());
    final BesuNode nodeC =
        besu.createIbft2NodeWithLocalAccountPermissioning(
            "nodeC", GENESIS_FILE, allowList, newPermissionsFile);

    waitForBlockHeight(bootnode, 2);

    nodeA.verify(beneficiary.balanceEquals(0));
    nodeA.execute(accountTransactions.createTransfer(sender, beneficiary, 1));
    nodeA.verify(beneficiary.balanceEquals(1));

    permissionedCluster.startNode(nodeC);

    waitForBlockHeight(bootnode, 4);
    waitForBlockHeight(nodeC, 4);
  }

  private void persistAllowList(final List<String> allowList, final Path path) throws IOException {
    AllowlistPersistor.addNewConfigItem(
        AllowlistPersistor.ALLOWLIST_TYPE.ACCOUNTS, allowList, path);
  }

  @After
  public void tearDown() {
    permissionedCluster.stop();
  }
}
