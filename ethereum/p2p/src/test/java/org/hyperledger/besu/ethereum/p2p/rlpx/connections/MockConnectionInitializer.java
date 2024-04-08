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

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage;
import org.hyperledger.besu.util.Subscribers;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MockConnectionInitializer implements ConnectionInitializer {
  private static final AtomicInteger NEXT_PORT = new AtomicInteger(0);

  private final PeerConnectionEventDispatcher eventDispatcher;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private boolean autocompleteConnections = true;
  private final Map<Peer, CompletableFuture<PeerConnection>> incompleteConnections =
      new HashMap<>();
  private int autoDisconnectCounter = 0;

  public MockConnectionInitializer(final PeerConnectionEventDispatcher eventDispatcher) {
    this.eventDispatcher = eventDispatcher;
  }

  public void setAutocompleteConnections(final boolean shouldAutocomplete) {
    this.autocompleteConnections = shouldAutocomplete;
  }

  public void completePendingFutures() {
    for (final Map.Entry<Peer, CompletableFuture<PeerConnection>> conn :
        incompleteConnections.entrySet()) {
      conn.getValue().complete(MockPeerConnection.create(conn.getKey()));
    }
    incompleteConnections.clear();
  }

  public void simulateIncomingConnection(final PeerConnection incomingConnection) {
    connectCallbacks.forEach(c -> c.onConnect(incomingConnection));
  }

  @Override
  public CompletableFuture<InetSocketAddress> start() {
    final InetSocketAddress socketAddress =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), NEXT_PORT.incrementAndGet());
    return CompletableFuture.completedFuture(socketAddress);
  }

  @Override
  public CompletableFuture<Void> stop() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public void subscribeIncomingConnect(final ConnectCallback callback) {
    connectCallbacks.subscribe(callback);
  }

  @Override
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    if (autoDisconnectCounter > 0) {
      autoDisconnectCounter--;
      final MockPeerConnection mockPeerConnection =
          MockPeerConnection.create(peer, eventDispatcher, false);
      mockPeerConnection.disconnect(DisconnectMessage.DisconnectReason.CLIENT_QUITTING);
      return CompletableFuture.completedFuture(mockPeerConnection);
    }
    if (autocompleteConnections) {
      return CompletableFuture.completedFuture(
          MockPeerConnection.create(peer, eventDispatcher, false));
    } else {
      final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
      incompleteConnections.put(peer, future);
      return future;
    }
  }

  public void setAutoDisconnectCounter(final int autoDisconnectCounter) {
    this.autoDisconnectCounter = autoDisconnectCounter;
  }
}
