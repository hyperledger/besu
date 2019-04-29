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
package tech.pegasys.pantheon.ethereum.p2p.network.netty;

import static java.util.Collections.unmodifiableCollection;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.messages.DisconnectMessage.DisconnectReason;
import tech.pegasys.pantheon.metrics.Counter;
import tech.pegasys.pantheon.metrics.LabelledMetric;
import tech.pegasys.pantheon.metrics.MetricCategory;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PeerConnectionRegistry implements DisconnectCallback {

  private final ConcurrentMap<BytesValue, PeerConnection> connections = new ConcurrentHashMap<>();

  private final LabelledMetric<Counter> disconnectCounter;
  private final Counter connectedPeersCounter;

  public PeerConnectionRegistry(final MetricsSystem metricsSystem) {
    disconnectCounter =
        metricsSystem.createLabelledCounter(
            MetricCategory.PEERS,
            "disconnected_total",
            "Total number of peers disconnected",
            "initiator",
            "disconnectReason");
    connectedPeersCounter =
        metricsSystem.createCounter(
            MetricCategory.PEERS, "connected_total", "Total number of peers connected");
    metricsSystem.createGauge(
        MetricCategory.PEERS,
        "peer_count_current",
        "Number of peers currently connected",
        () -> (double) connections.size());
  }

  public void registerConnection(final PeerConnection connection) {
    connections.put(connection.getPeerInfo().getNodeId(), connection);
    connectedPeersCounter.inc();
  }

  public Collection<PeerConnection> getPeerConnections() {
    return unmodifiableCollection(connections.values());
  }

  public int size() {
    return connections.size();
  }

  public boolean isAlreadyConnected(final BytesValue nodeId) {
    return connections.containsKey(nodeId);
  }

  public Optional<PeerConnection> getConnectionForPeer(final BytesValue nodeID) {
    return Optional.ofNullable(connections.get(nodeID));
  }

  @Override
  public void onDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    connections.remove(connection.getPeerInfo().getNodeId());
    disconnectCounter.labels(initiatedByPeer ? "remote" : "local", reason.name()).inc();
  }
}
