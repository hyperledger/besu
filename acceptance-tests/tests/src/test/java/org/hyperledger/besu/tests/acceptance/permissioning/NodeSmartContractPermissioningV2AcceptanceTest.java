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

import org.junit.Before;
import org.junit.Test;

public class NodeSmartContractPermissioningV2AcceptanceTest
    extends NodeSmartContractPermissioningV2AcceptanceTestBase {

  private Node bootnode;
  private Node permissionedNode;
  private Node allowedNode;
  private Node forbiddenNode;

  @Before
  public void setUp() {
    bootnode = bootnode("bootnode");
    forbiddenNode = node("forbidden-node");
    allowedNode = node("allowed-node");
    permissionedNode = permissionedNode("permissioned-node");

    permissionedCluster.start(bootnode, forbiddenNode, allowedNode, permissionedNode);

    // updating permissioning smart contract with allowed nodes

    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(connectionIsAllowed(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(connectionIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(connectionIsAllowed(permissionedNode));
  }

  @Test
  public void permissionedNodeShouldPeerOnlyWithAllowedNodes() {
    bootnode.verify(net.awaitPeerCount(3));
    allowedNode.verify(net.awaitPeerCount(3));
    forbiddenNode.verify(net.awaitPeerCount(2));
    permissionedNode.verify(net.awaitPeerCount(2));
  }

  @Test
  public void permissionedNodeShouldDisconnectFromNodeNotPermittedAnymore() {
    permissionedNode.verify(admin.addPeer(bootnode));
    permissionedNode.verify(admin.addPeer(allowedNode));
    permissionedNode.verify(net.awaitPeerCount(2));

    permissionedNode.execute(forbidNode(allowedNode));
    permissionedNode.verify(connectionIsForbidden(allowedNode));

    permissionedNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void permissionedNodeShouldConnectToNewlyPermittedNode() {
    permissionedNode.verify(admin.addPeer(bootnode));
    permissionedNode.verify(admin.addPeer(allowedNode));
    permissionedNode.verify(net.awaitPeerCount(2));

    permissionedNode.execute(allowNode(forbiddenNode));
    permissionedNode.verify(connectionIsAllowed(forbiddenNode));
    permissionedNode.verify(admin.addPeer(forbiddenNode));

    permissionedNode.verify(net.awaitPeerCount(3));
  }

  @Test
  public void permissioningUpdatesPropagateThroughNetwork() {
    permissionedNode.verify(admin.addPeer(bootnode));
    permissionedNode.verify(admin.addPeer(allowedNode));
    permissionedNode.verify(net.awaitPeerCount(2));

    // permissioning changes in peer should propagate to permissioned node
    allowedNode.execute(allowNode(forbiddenNode));
    allowedNode.verify(connectionIsAllowed(forbiddenNode));
    permissionedNode.verify(connectionIsAllowed(forbiddenNode));

    permissionedNode.verify(admin.addPeer(forbiddenNode));
    permissionedNode.verify(net.awaitPeerCount(3));
  }

  @Test
  public void onchainPermissioningAllowlistShouldPersistAcrossRestarts() {
    permissionedCluster.stop();
    permissionedCluster.start(bootnode, forbiddenNode, allowedNode, permissionedNode);

    permissionedNode.verify(connectionIsAllowed(allowedNode));
    permissionedNode.verify(connectionIsAllowed(bootnode));
    permissionedNode.verify(connectionIsAllowed(permissionedNode));
    permissionedNode.verify(connectionIsForbidden(forbiddenNode));
  }
}
