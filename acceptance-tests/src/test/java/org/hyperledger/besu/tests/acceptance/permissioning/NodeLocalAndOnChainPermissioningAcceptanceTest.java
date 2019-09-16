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
package org.hyperledger.besu.tests.acceptance.permissioning;

import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.Before;
import org.junit.Test;

public class NodeLocalAndOnChainPermissioningAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {

  private Node bootnode;
  private Node permissionedNode;
  private Node allowedNode;
  private Node forbiddenNode;

  @Before
  public void setUp() {
    bootnode = bootnode("bootnode");
    forbiddenNode = node("forbidden-node");
    allowedNode = node("allowed-node");

    permissionedCluster.start(bootnode, allowedNode, forbiddenNode);
  }

  @Test
  public void testNodeCannotConnectWhenWhiteListedOnChainButNotLocal() {

    // add permissioned node after cluster start because we need enode URI for local config
    permissionedNode = permissionedNode("permissioned-node", bootnode, allowedNode);
    permissionedCluster.addNode(permissionedNode);

    // update OnChain smart contract with allowed nodes
    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(nodeIsAllowed(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(nodeIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(nodeIsAllowed(permissionedNode));

    permissionedNode.execute(allowNode(forbiddenNode));
    permissionedNode.verify(nodeIsAllowed(forbiddenNode));

    permissionedNodeShouldDiscoverOnlyAllowedNodes();
  }

  @Test
  public void testNodeCannotConnectWhenWhitelistedLocalButNotOnChain() {
    // onchain whitelist: A, B
    // local whitelist: A, B, C

    // add permissioned node after cluster start because we need enode URI for local config
    permissionedNode = permissionedNode("permissioned-node", bootnode, allowedNode, forbiddenNode);
    permissionedCluster.addNode(permissionedNode);

    // update OnChain smart contract with allowed nodes
    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(nodeIsAllowed(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(nodeIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(nodeIsAllowed(permissionedNode));

    permissionedNodeShouldDiscoverOnlyAllowedNodes();
  }

  @Test
  public void testNodesCanConnectWhenWhitelistedBothOnChainAndLocal() {
    // add permissioned node after cluster start because we need enode URI for local config
    permissionedNode = permissionedNode("permissioned-node", bootnode, allowedNode, forbiddenNode);
    permissionedCluster.addNode(permissionedNode);

    // update OnChain smart contract with allowed nodes
    permissionedNode.execute(allowNode(bootnode));
    permissionedNode.verify(nodeIsAllowed(bootnode));

    permissionedNode.execute(allowNode(allowedNode));
    permissionedNode.verify(nodeIsAllowed(allowedNode));

    permissionedNode.execute(allowNode(permissionedNode));
    permissionedNode.verify(nodeIsAllowed(permissionedNode));

    permissionedNode.execute(allowNode(forbiddenNode));
    permissionedNode.verify(nodeIsAllowed(forbiddenNode));

    bootnode.verify(net.awaitPeerCount(3));
    allowedNode.verify(net.awaitPeerCount(3));
    forbiddenNode.verify(net.awaitPeerCount(3));
    permissionedNode.verify(net.awaitPeerCount(3));
  }

  private void permissionedNodeShouldDiscoverOnlyAllowedNodes() {
    bootnode.verify(net.awaitPeerCount(3));
    allowedNode.verify(net.awaitPeerCount(3));
    forbiddenNode.verify(net.awaitPeerCount(2));
    permissionedNode.verify(net.awaitPeerCount(2));
  }
}
