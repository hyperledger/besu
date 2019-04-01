/*
 * Copyright 2018 ConsenSys AG.
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

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.Before;
import org.junit.Test;

public class LocalConfigNodePermissioningAcceptanceTest extends AcceptanceTestBase {

  private Cluster permissionedCluster;
  private Node bootnode;
  private Node forbiddenNode;
  private Node allowedNode;
  private Node permissionedNode;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().setAwaitPeerDiscovery(false).build();

    permissionedCluster = new Cluster(clusterConfiguration, net);
    bootnode = pantheon.createArchiveNode("bootnode");
    forbiddenNode = pantheon.createArchiveNodeThatMustNotBeTheBootnode("forbidden-node");
    allowedNode = pantheon.createArchiveNode("allowed-node");

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
  public void permissionedNodeShouldDisconnectFromNodeRemovedFromWhitelist() {
    permissionedNode.verify(net.awaitPeerCount(2));

    // remove node from the whitelist
    permissionedNode.verify(perm.removeNodesFromWhitelist(allowedNode));
    permissionedNode.verify(perm.getNodesWhitelist(1));

    permissionedNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void forbiddenNodeAddedToWhitelistCanConnectToPermissionedNode() {
    permissionedNode.verify(net.awaitPeerCount(2));

    // add node to the whitelist
    permissionedNode.verify(perm.addNodesToWhitelist(forbiddenNode));
    permissionedNode.verify(perm.getNodesWhitelist(3));

    permissionedNode.verify(admin.addPeer(forbiddenNode));
    permissionedNode.verify(net.awaitPeerCount(3));
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    permissionedCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
