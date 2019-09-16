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
package org.hyperledger.besu.ethereum.p2p.rlpx.connections;

import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;
import org.hyperledger.besu.util.Subscribers;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MockConnectionInitializer implements ConnectionInitializer {
  private static AtomicInteger NEXT_PORT = new AtomicInteger(0);

  private final PeerConnectionEventDispatcher eventDispatcher;
  private final Subscribers<ConnectCallback> connectCallbacks = Subscribers.create();
  private boolean autocompleteConnections = true;
  private Map<Peer, CompletableFuture<PeerConnection>> incompleteConnections = new HashMap<>();

  public MockConnectionInitializer(final PeerConnectionEventDispatcher eventDispatcher) {
    this.eventDispatcher = eventDispatcher;
  }

  public void setAutocompleteConnections(final boolean shouldAutocomplete) {
    this.autocompleteConnections = shouldAutocomplete;
  }

  public void completePendingFutures() {
    for (Entry<Peer, CompletableFuture<PeerConnection>> conn : incompleteConnections.entrySet()) {
      conn.getValue().complete(MockPeerConnection.create(conn.getKey()));
    }
    incompleteConnections.clear();
  }

  public void simulateIncomingConnection(final PeerConnection incomingConnection) {
    connectCallbacks.forEach(c -> c.onConnect(incomingConnection));
  }

  @Override
  public CompletableFuture<Integer> start() {
    return CompletableFuture.completedFuture(NEXT_PORT.incrementAndGet());
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
    if (autocompleteConnections) {
      return CompletableFuture.completedFuture(MockPeerConnection.create(peer, eventDispatcher));
    } else {
      final CompletableFuture<PeerConnection> future = new CompletableFuture<>();
      incompleteConnections.put(peer, future);
      return future;
    }
  }
}
