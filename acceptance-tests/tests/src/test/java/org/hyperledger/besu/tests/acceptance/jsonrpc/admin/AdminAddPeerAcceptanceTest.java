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
package org.hyperledger.besu.tests.acceptance.jsonrpc.admin;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.Cluster;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AdminAddPeerAcceptanceTest extends AcceptanceTestBase {
  private Cluster noDiscoveryCluster;

  private Node nodeA;
  private Node nodeB;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    noDiscoveryCluster = new Cluster(clusterConfiguration, net);
    nodeA = besu.createArchiveNodeWithDiscoveryDisabledAndAdmin("nodeA");
    nodeB = besu.createArchiveNodeWithDiscoveryDisabledAndAdmin("nodeB");
    noDiscoveryCluster.start(nodeA, nodeB);
  }

  @After
  public void tearDown() {
    noDiscoveryCluster.stop();
  }

  @Test
  public void adminAddPeerForcesConnection() {
    nodeA.verify(net.awaitPeerCount(0));
    nodeA.verify(admin.addPeer(nodeB));
    nodeA.verify(net.awaitPeerCount(1));
    nodeB.verify(net.awaitPeerCount(1));
  }
}
