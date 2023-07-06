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
package org.hyperledger.besu.ethereum.eth.transactions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.TestNode.shortId;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.Vertx;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestNodeList implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(TestNodeList.class);
  protected final List<TestNode> nodes = new ArrayList<>();
  private final Duration MSG_WAIT = Duration.ofSeconds(2);

  public TestNode create(
      final Vertx vertx,
      final Integer port,
      final KeyPair kp,
      final DiscoveryConfiguration discoveryCfg)
      throws IOException {
    final TestNode node = new TestNode(vertx, port, kp, discoveryCfg);
    nodes.add(node);
    return node;
  }

  public void connectAndAssertAll()
      throws InterruptedException, ExecutionException, TimeoutException {
    for (int i = 0; i < nodes.size(); i++) {
      final TestNode source = nodes.get(i);
      for (int j = i + 1; j < nodes.size(); j++) {
        final TestNode destination = nodes.get(j);
        try {
          LOG.info("Attempting to connect source " + source.shortId() + " to dest " + destination);
          assertThat(
                  source.connect(destination).get(30L, TimeUnit.SECONDS).getPeerInfo().getNodeId())
              .isEqualTo(destination.id());
          // Wait for the destination node to finish bonding.
          Awaitility.await()
              .atMost(30, TimeUnit.SECONDS)
              .until(() -> hasConnection(destination, source));
          LOG.info("Successfully connected       " + source.shortId() + " to dest " + destination);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
          final String msg =
              format(
                  "Error connecting source node %s to destination node %s in time allotted.",
                  source.shortId(), destination.shortId());
          LOG.error(msg, e);
          throw e;
        }
      }
    }
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public void assertPeerCounts() {
    int errCnt = 0;
    final StringBuilder sb = new StringBuilder();

    final int expectedCount = nodes.size() - 1; // Connections to every other node, but not itself
    for (final TestNode node : nodes) {
      final int actualCount = node.network.getPeers().size();
      if (expectedCount != actualCount) {
        sb.append(
            "Node "
                + node.shortId()
                + " expected "
                + expectedCount
                + " peer connections, but actually has "
                + actualCount
                + " peer connections.\n");
        errCnt++;
      }
    }
    final String header = "" + errCnt + " Nodes have missing peer connections.\n";
    assertThat(errCnt).describedAs(header + sb).isEqualTo(0);
  }

  private boolean hasConnection(final TestNode node1, final TestNode node2) {
    for (final PeerConnection peer : node1.network.getPeers()) {
      if (node2.id().equals(peer.getPeerInfo().getNodeId())) {
        return true;
      }
    }
    return false;
  }

  private Collection<TestNode> findMissingConnections(final TestNode testNode) {
    final Collection<TestNode> missingConnections = new HashSet<>();
    for (final TestNode node : nodes) {
      if (testNode == node) continue; // don't expect connections to self
      if (!hasConnection(testNode, node)) {
        missingConnections.add(node);
      }
    }
    return missingConnections;
  }

  private Collection<TestNode> findExtraConnections(final TestNode testNode) {
    final Collection<TestNode> extraConnections = new HashSet<>(nodes);
    extraConnections.removeIf(next -> hasConnection(testNode, next) || testNode == next);
    return extraConnections;
  }

  /** Assert that all Nodes have exactly 1 connection to each other node */
  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  public void assertPeerConnections() {
    int errCnt = 0;
    final StringBuilder sb = new StringBuilder();
    Collection<TestNode> incorrectConnections;

    for (final TestNode node1 : nodes) {
      incorrectConnections = findMissingConnections(node1);
      for (final TestNode missingConnection : incorrectConnections) {
        sb.append(
            "Node "
                + node1.shortId()
                + " is missing connection to node "
                + missingConnection.shortId()
                + ".\n");
        errCnt++;
      }

      incorrectConnections = findExtraConnections(node1);
      for (final TestNode extraConnection : incorrectConnections) {
        sb.append(
            "Node "
                + node1.shortId()
                + " has unexpected connection to node "
                + extraConnection.shortId()
                + ".\n");
        errCnt++;
      }
    }
    final String header = "There are " + errCnt + " incorrect peer connections.\n";
    assertThat(errCnt).describedAs(header + sb).isEqualTo(0);
  }

  public void assertPendingTransactionCounts(final int... expected) {
    checkNotNull(expected);
    checkArgument(
        expected.length == nodes.size(),
        "Expected values for %s nodes, but got %s.",
        expected.length,
        nodes.size());
    int errCnt = 0;
    final StringBuilder sb = new StringBuilder();
    int i = 0;
    for (final TestNode node : nodes) {
      final int expectedCnt = expected[i];
      try {
        Awaitility.await()
            .atMost(MSG_WAIT)
            .until(() -> node.getPendingTransactionCount() == expectedCnt);
      } catch (final ConditionTimeoutException e) {
        /* Ignore ConditionTimeoutException.  We just want a fancy wait here.  The real check will happen
          below and has a proper exception message
        */
      }

      final int actual = node.getPendingTransactionCount();
      if (actual != expected[i]) {
        errCnt++;
        final String msg =
            format(
                "Node %s expected %d pending txs, but has %d.\n",
                node.shortId(), expected[i], actual);
        sb.append(msg);
      }
      i++;
    }
    final String header = "Nodes have " + errCnt + " incorrect Pending Tx pool sizes.\n";
    assertThat(errCnt).describedAs(header + sb).isEqualTo(0);
  }

  /** Assert that there were no node disconnections reported from the P2P network */
  public void assertNoNetworkDisconnections() {
    int errCnt = 0;
    final StringBuilder sb = new StringBuilder();
    for (final TestNode node : nodes) {
      for (final Map.Entry<PeerConnection, DisconnectReason> entry :
          node.disconnections.entrySet()) {
        final PeerConnection peer = entry.getKey();
        final String peerString = peer.getPeerInfo().getNodeId() + "@" + peer.getRemoteAddress();
        final String unsentTxMsg =
            "Node "
                + node.shortId()
                + " has received a disconnection from "
                + peerString
                + " for "
                + entry.getValue()
                + "\n";
        sb.append(unsentTxMsg);
        errCnt++;
      }
    }
    final String header = "Nodes have received " + errCnt + " disconnections.\n";
    assertThat(errCnt).describedAs(header + sb).isEqualTo(0);
  }

  /** Logs the Peer connections for each node. */
  public void logPeerConnections() {
    final List<String> connStr = new ArrayList<>();
    for (final TestNode node : nodes) {
      for (final PeerConnection peer : node.network.getPeers()) {
        final String localString = node.shortId() + "@" + peer.getLocalAddress();
        final String peerString =
            shortId(peer.getPeerInfo().getNodeId()) + "@" + peer.getRemoteAddress();
        connStr.add("Connection: " + localString + " to " + peerString);
      }
    }
    LOG.info("TestNodeList Connections: {}", connStr);
  }

  @Override
  public void close() throws IOException {
    IOException firstEx = null;

    for (final Iterator<TestNode> it = nodes.iterator(); it.hasNext(); )
      try {
        final TestNode node = it.next();
        node.close();
        it.remove();
      } catch (final IOException e) {
        if (firstEx == null) firstEx = e;
        LOG.warn("Error closing.  Continuing", e);
      }
    if (firstEx != null)
      throw new IOException("Unable to close node resources.  Wrapping first exception.", firstEx);
  }

  public TestNode get(final int index) {
    return nodes.get(index);
  }

  public void add(final int index, final TestNode element) {
    nodes.add(index, element);
  }
}
