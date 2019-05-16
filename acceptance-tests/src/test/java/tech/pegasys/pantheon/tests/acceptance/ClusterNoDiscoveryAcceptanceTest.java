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
package tech.pegasys.pantheon.tests.acceptance;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.Before;
import org.junit.Test;

public class ClusterNoDiscoveryAcceptanceTest extends AcceptanceTestBase {

  private Node fullNode;
  private Node noDiscoveryNode;
  private Cluster noDiscoveryCluster;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    noDiscoveryCluster = new Cluster(clusterConfiguration, net);
    noDiscoveryNode = pantheon.createNodeWithNoDiscovery("noDiscovery");
    fullNode = pantheon.createArchiveNode("node2");
    noDiscoveryCluster.start(noDiscoveryNode, fullNode);
  }

  @Test
  public void shouldNotConnectToOtherPeer() {
    fullNode.verify(net.awaitPeerCount(0));
  }

  @Override
  public void tearDownAcceptanceTestBase() {
    noDiscoveryCluster.stop();
    super.tearDownAcceptanceTestBase();
  }
}
