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
package tech.pegasys.pantheon.tests.acceptance.jsonrpc.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.Cluster;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.cluster.ClusterConfigurationBuilder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class AdminAddPeerWithP2PDisabledAcceptanceTest extends AcceptanceTestBase {
  private PantheonNode nodeA;
  private PantheonNode nodeB;
  private Cluster p2pDisabledCluster;

  @Before
  public void setUp() throws Exception {
    final ClusterConfiguration clusterConfiguration =
        new ClusterConfigurationBuilder().setAwaitPeerDiscovery(false).build();
    p2pDisabledCluster = new Cluster(clusterConfiguration, net);
    nodeA = pantheon.createNodeWithP2pDisabledAndAdmin("nodeA");
    nodeB = pantheon.createArchiveNodeWithDiscoveryDisabledAndAdmin("nodeB");
    p2pDisabledCluster.start(nodeA, nodeB);
  }

  @After
  public void tearDown() {
    p2pDisabledCluster.stop();
  }

  @Test
  public void adminAddPeerFailsWhenP2PDisabledOnTargetNode() {
    final String nodeBEnode = nodeB.enodeUrl();
    try {
      nodeA.verify(admin.addPeer(nodeBEnode));
      fail("expected exception because P2P is disabled");
    } catch (ClientConnectionException e) {
      assertThat(e.getMessage().contains("P2P has been disabled")).isTrue();
    }
    nodeB.verify(net.awaitPeerCount(0));
  }

  @Test
  public void adminAddPeerHasNoEffectWhenP2PDisabledOnInputNode() {
    final String nodeAEnode = nodeA.enodeUrl();
    nodeB.verify(admin.addPeer(nodeAEnode));
    nodeB.verify(net.awaitPeerCount(0));
  }
}
