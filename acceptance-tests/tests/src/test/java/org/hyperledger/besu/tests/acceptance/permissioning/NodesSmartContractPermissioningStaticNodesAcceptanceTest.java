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

import static java.util.stream.Collectors.toList;

import org.hyperledger.besu.tests.acceptance.dsl.node.Node;
import org.hyperledger.besu.tests.acceptance.dsl.node.RunnableNode;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

import org.junit.Before;
import org.junit.Test;

public class NodesSmartContractPermissioningStaticNodesAcceptanceTest
    extends NodeSmartContractPermissioningAcceptanceTestBase {

  private Node miner;
  private Node permissionedNode;

  @Before
  public void setUp() {
    miner = miner("miner");
    permissionedCluster.start(miner);
  }

  @Test
  public void onlyTrustStaticNodesWhileOutOfSync() {
    // wait for some blocks so the permissioned node has some syncing to do
    waitForBlockHeight(miner, 25);
    stopMining(miner);

    // start permissioned node with miner node in the static nodes list
    permissionedNode = permissionedNodeWithStaticNodes(Arrays.asList(miner));
    permissionedCluster.addNode(permissionedNode);

    // as soon as we start the node should connect to static nodes
    permissionedNode.verify(net.awaitPeerCount(1));
    waitForBlockHeight(permissionedNode, 25);

    // after syncing up with the network the node won't trust static nodes anymore
    permissionedNode.verify(net.awaitPeerCount(0));
  }

  private void stopMining(final Node node) {
    node.execute(minerTransactions.minerStop());
    node.verify(eth.miningStatus(false));
  }

  private Node permissionedNodeWithStaticNodes(final List<Node> staticNodes) {
    return permissionedNodeBuilder
        .name("node-with-static-nodes")
        .genesisFile(GENESIS_FILE)
        .nodesContractEnabled(CONTRACT_ADDRESS)
        .staticNodes(mapNodesToEnodeURLs(staticNodes))
        .disableMining()
        .build();
  }

  @Nonnull
  private List<String> mapNodesToEnodeURLs(final List<Node> staticNodes) {
    return staticNodes.stream()
        .map(node -> (RunnableNode) node)
        .map(RunnableNode::enodeUrl)
        .map(URI::toASCIIString)
        .collect(toList());
  }
}
