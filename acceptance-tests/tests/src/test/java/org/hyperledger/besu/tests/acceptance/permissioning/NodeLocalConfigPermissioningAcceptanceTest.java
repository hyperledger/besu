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
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.Before;
import org.junit.Test;

public class NodeLocalConfigPermissioningAcceptanceTest extends AcceptanceTestBase {

  private Cluster permissionedCluster;
  private Node bootnode;
  private Node forbiddenNode;
  private Node allowedNode;
  private Node permissionedNode;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();

    permissionedCluster = new Cluster(clusterConfiguration, net);
    bootnode = besu.createArchiveNode("bootnode");
    forbiddenNode = besu.createArchiveNodeThatMustNotBeTheBootnode("forbidden-node");
    allowedNode = besu.createArchiveNode("allowed-node");

    permissionedCluster.start(bootnode, allowedNode, forbiddenNode);

    permissionedNode =
        permissionedNodeBuilder.nodesPermittedInConfig(bootnode, allowedNode).build();

    permissionedCluster.addNode(permissionedNode);
  }

  @Test
  public void permissionedNodeShouldDiscoverOnlyAllowedNodes() {
    bootnode.verify(net.awaitPeerCount(3));
    allowedNode.verify(net.awaitPeerCount(3));
    forbiddenNode.verify(net.awaitPeerCount(2));
    permissionedNode.verify(net.awaitPeerCount(2));
  }

  @Test
  public void permissionedNodeShouldDisconnectFromNodeRemovedFromAllowlist() {
    permissionedNode.verify(net.awaitPeerCount(2));

    // remove node from the allowlist
    permissionedNode.verify(perm.removeNodesFromAllowlist(allowedNode));
    permissionedNode.verify(perm.getNodesAllowlist(1));

    permissionedNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void forbiddenNodeAddedToAllowlistCanConnectToPermissionedNode() {
    permissionedNode.verify(net.awaitPeerCount(2));

    // add node to the allowlist
    permissionedNode.verify(perm.addNodesToAllowlist(forbiddenNode));
    permissionedNode.verify(perm.getNodesAllowlist(3));

    permissionedNode.verify(admin.addPeer(forbiddenNode));
    permissionedNode.verify(net.awaitPeerCount(3));
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    permissionedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
