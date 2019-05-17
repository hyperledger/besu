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
package tech.pegasys.pantheon.tests.acceptance.jsonrpc;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.Node;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.Test;

public class NetServicesAcceptanceTest extends AcceptanceTestBase {

  private Cluster noDiscoveryCluster;

  private Node nodeA;
  private Node nodeB;

  @Test
  public void shouldIndicateNetServicesEnabled() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    noDiscoveryCluster = new Cluster(clusterConfiguration, net);
    nodeA = pantheon.createArchiveNodeNetServicesEnabled("nodeA");
    nodeB = pantheon.createArchiveNodeNetServicesEnabled("nodeB");
    noDiscoveryCluster.start(nodeA, nodeB);

    nodeA.verify(net.netServicesAllActive());
    nodeB.verify(net.netServicesAllActive());
  }

  @Test
  public void shouldNotDisplayDisabledServices() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().awaitPeerDiscovery(false).build();
    noDiscoveryCluster = new Cluster(clusterConfiguration, net);
    nodeA = pantheon.createArchiveNodeNetServicesDisabled("nodeA");
    nodeB = pantheon.createArchiveNodeNetServicesDisabled("nodeB");
    noDiscoveryCluster.start(nodeA, nodeB);

    nodeA.verify(net.netServicesOnlyJsonRpcEnabled());
    nodeB.verify(net.netServicesOnlyJsonRpcEnabled());
  }
}
