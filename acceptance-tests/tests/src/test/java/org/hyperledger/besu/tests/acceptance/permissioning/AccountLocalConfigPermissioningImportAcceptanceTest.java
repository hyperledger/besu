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

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.account.Account;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AccountLocalConfigPermissioningImportAcceptanceTest extends AcceptanceTestBase {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private static final String GENESIS_FILE = "/ibft/ibft.json";

  private Account senderA;
  private Account senderB;
  private Account beneficiary;
  private BesuNode bootnode;
  private BesuNode nodeA;
  private BesuNode nodeB;
  private BesuNode nodeC;
  private Cluster permissionedCluster;
  private File file;

  @Before
  public void setUp() throws IOException {
    senderA = accounts.getPrimaryBenefactor();
    senderB = accounts.getSecondaryBenefactor();
    beneficiary = accounts.createAccount("beneficiary");
    final List<String> allowList =
        List.of(senderA.getAddress(), senderB.getAddress(), beneficiary.getAddress());
    file = folder.newFile();
    bootnode = besu.createIbft2NonValidatorBootnode("bootnode", GENESIS_FILE);
    nodeA =
        besu.createIbft2NodeWithLocalAccountPermissioning("nodeA", GENESIS_FILE, allowList, file);
    nodeB =
        besu.createIbft2NodeWithLocalAccountPermissioning("nodeB", GENESIS_FILE, allowList, file);
    permissionedCluster =
        new Cluster(new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build(), net);
  }

  @Test
  public void transactionFromDeniedAccountShouldNotBreakBlockImport() throws IOException {
    permissionedCluster.start(bootnode, nodeA, nodeB);

    waitForBlockHeight(bootnode, 5);

    nodeA.verify(beneficiary.balanceEquals(0));
    nodeA.execute(accountTransactions.createTransfer(senderA, beneficiary, 1));
    nodeA.verify(beneficiary.balanceEquals(1));
    nodeC =
        besu.createIbft2NodeWithLocalAccountPermissioning(
            "nodeC", GENESIS_FILE, List.of(senderB.getAddress(), beneficiary.getAddress()), file);
    permissionedCluster.startNode(nodeC);

    waitForBlockHeight(bootnode, 10);
    waitForBlockHeight(nodeC, 10);
  }

  @After
  public void tearDown() {
    permissionedCluster.stop();
  }
}
