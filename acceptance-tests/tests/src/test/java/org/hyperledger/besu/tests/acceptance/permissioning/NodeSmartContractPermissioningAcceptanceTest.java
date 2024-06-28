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

public class NodeSmartContractPermissioningAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {

  private Node bootnode;
  private Node permissionedNode;
  private Node allowedNode;
  private Node forbiddenNode;

  @BeforeEach
  public void setUp() {
    bootnode = bootnode("bootnode");
    forbiddenNode = node("forbidden-node");
    allowedNode = node("allowed-node");
    permissionedNode = permissionedNode("permissioned-node");

    permissionedCluster.start(bootnode, forbiddenNode, allowedNode, permissionedNode);

    // updating permissioning smart contract with allowed nodes
    permissionedNode.verify(nodeIsForbidden(bootnode));
    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(nodeIsAllowed(bootnode));
    permissionedNode.verify(admin.hasPeer(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(nodeIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(nodeIsAllowed(permissionedNode));

    permissionedNode.verify(admin.addPeer(allowedNode));

    allowedNode.verify(eth.syncingStatus(false));
    bootnode.verify(eth.syncingStatus(false));
    permissionedNode.verify(eth.syncingStatus(false));
    forbiddenNode.verify(eth.syncingStatus(false));
  }

  @Test
  @Disabled("test is flaky")
  public void permissionedNodeShouldPeerOnlyWithAllowedNodes() {
    bootnode.verify(net.awaitPeerCount(3));
    allowedNode.verify(net.awaitPeerCount(3));
    forbiddenNode.verify(net.awaitPeerCount(2));
    permissionedNode.verify(net.awaitPeerCount(2));
  }

  @Test
  public void permissionedNodeShouldDisconnectFromNodeNotPermittedAnymore() {
    permissionedNode.execute(forbidNode(allowedNode));
    permissionedNode.verify(connectionIsForbidden(permissionedNode, allowedNode));

    permissionedNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void permissioningUpdatesPropagateThroughNetwork() {
    // connection to newly permitted node is allowed
    allowedNode.execute(allowNode(forbiddenNode));
    allowedNode.verify(connectionIsAllowed(permissionedNode, forbiddenNode));
    // permissioning changes in peer should propagate to permissioned node
    permissionedNode.verify(connectionIsAllowed(permissionedNode, forbiddenNode));

    permissionedNode.verify(admin.addPeer(forbiddenNode));
    permissionedNode.verify(net.awaitPeerCount(3));
  }

  @Test
  public void onchainPermissioningAllowlistShouldPersistAcrossRestarts() {
    permissionedCluster.stop();
    permissionedCluster.start(bootnode, forbiddenNode, allowedNode, permissionedNode);

    permissionedNode.verify(nodeIsAllowed(allowedNode));
    permissionedNode.verify(nodeIsAllowed(bootnode));
    permissionedNode.verify(nodeIsAllowed(permissionedNode));
  }
}
