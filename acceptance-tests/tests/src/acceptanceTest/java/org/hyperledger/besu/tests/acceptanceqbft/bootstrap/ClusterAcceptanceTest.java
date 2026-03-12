/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptanceqbft.bootstrap;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.Node;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterAcceptanceTest extends AcceptanceTestBase {

  private Node minerNode;
  private Node fullNode;

  @BeforeEach
  public void setUp() throws Exception {
    minerNode = besu.createQbftNode("node1");
    fullNode = besu.createQbftNode("node2");
    cluster.start(minerNode, fullNode);
  }

  @Test
  public void shouldConnectToOtherPeer() {
    minerNode.verify(net.awaitPeerCount(1));
    fullNode.verify(net.awaitPeerCount(1));
  }

  @Test
  public void shouldRestartAfterStop() {
    minerNode.verify(net.awaitPeerCount(1));
    fullNode.verify(net.awaitPeerCount(1));
    cluster.stop();

    // Allow time for network resources to be released before restart
    try {
      Thread.sleep(2000); // 2 second delay
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    cluster.start(minerNode, fullNode);
    // Use longer timeout for peer reconnection after restart
    minerNode.verify(net.awaitPeerCount(1, 90)); // 90 second timeout
    fullNode.verify(net.awaitPeerCount(1, 90));
  }
}
