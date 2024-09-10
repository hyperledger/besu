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

import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class NodeSmartContractPermissioningOutOfSyncAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {
  private Node bootnode;
  private Node permissionedNodeA;
  private Node permissionedNodeB;

  @BeforeEach
  public void setUp() throws InterruptedException {
    bootnode = bootnode("bootnode");
    permissionedNodeA = permissionedNode("permissioned-node-A");
    permissionedNodeB = permissionedNode("permissioned-node-B");

    permissionedCluster.start(bootnode, permissionedNodeA);

    // update onchain smart contract to allowlist nodes
    permissionedNodeA.execute(allowNode(bootnode));
    permissionedNodeA.verify(nodeIsAllowed(bootnode));
    permissionedNodeA.execute(allowNode(permissionedNodeA));
    permissionedNodeA.verify(nodeIsAllowed(permissionedNodeA));
    permissionedNodeA.verify(admin.addPeer(bootnode));
  }

  @Test
  @Disabled("test is flaky #7108")
  public void addNodeToClusterAndVerifyNonBootNodePeerConnectionWorksAfterSync() {
    final long blockchainHeight = 25L;
    waitForBlockHeight(permissionedNodeA, blockchainHeight);

    // verify Node A is in sync with bootnode
    final var minerChainHead = bootnode.execute(ethTransactions.block());
    permissionedNodeA.verify(blockchain.minimumHeight(minerChainHead.getNumber().longValue()));

    // check that connection is forbidden (before node b is permitted)
    permissionedCluster.addNode(permissionedNodeB);
    permissionedNodeB.verify(connectionIsForbidden(permissionedNodeA, permissionedNodeB));

    // Permit Node B
    permissionedNodeA.execute(allowNode(permissionedNodeB));
    permissionedNodeA.verify(admin.addPeer(permissionedNodeB));

    // connection should be allowed after node B syncs
    waitForBlockHeight(permissionedNodeB, blockchainHeight);
    permissionedNodeB.verify(connectionIsAllowed(permissionedNodeA, permissionedNodeB));
  }
}
