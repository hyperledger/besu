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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.rlpx.DisconnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.MessageCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.hyperledger.besu.util.Subscribers;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PeerConnectionEvents implements PeerConnectionEventDispatcher {
  private final Subscribers<DisconnectCallback> disconnectSubscribers = Subscribers.create(true);
  private final Map<Capability, Subscribers<MessageCallback>> messageSubscribers =
      new ConcurrentHashMap<>();
  private final LabelledMetric<Counter> disconnectCounter;

  public PeerConnectionEvents(final MetricsSystem metricsSystem) {
    disconnectCounter =
        metricsSystem.createLabelledCounter(
            BesuMetricCategory.PEERS,
            "disconnected_total",
            "Total number of peers disconnected",
            "initiator",
            "disconnectReason");
  }

  @Override
  public void dispatchDisconnect(
      final PeerConnection connection,
      final DisconnectReason reason,
      final boolean initiatedByPeer) {
    disconnectCounter.labels(initiatedByPeer ? "remote" : "local", reason.name()).inc();
    disconnectSubscribers.forEach(s -> s.onDisconnect(connection, reason, initiatedByPeer));
  }

  @Override
  public void dispatchMessage(
      final Capability capability, final PeerConnection connection, final MessageData message) {
    final Message msg = new DefaultMessage(connection, message);
    messageSubscribers
        .getOrDefault(capability, Subscribers.none())
        .forEach(s -> s.onMessage(capability, msg));
  }

  public void subscribeDisconnect(final DisconnectCallback callback) {
    disconnectSubscribers.subscribe(callback);
  }

  public void subscribeMessage(final Capability capability, final MessageCallback callback) {
    messageSubscribers
        .computeIfAbsent(capability, key -> Subscribers.create(true))
        .subscribe(callback);
  }
}
