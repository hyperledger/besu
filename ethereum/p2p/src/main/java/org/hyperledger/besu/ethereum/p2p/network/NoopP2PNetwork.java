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
package org.hyperledger.besu.ethereum.p2p.network;

import org.hyperledger.besu.ethereum.p2p.discovery.DiscoveryPeer;
import org.hyperledger.besu.ethereum.p2p.network.exceptions.P2PDisabledException;
import org.hyperledger.besu.ethereum.p2p.peers.Peer;
import org.hyperledger.besu.ethereum.p2p.rlpx.ConnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.DisconnectCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.MessageCallback;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.ShouldConnectCallback;
import org.hyperledger.besu.plugin.data.EnodeURL;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class NoopP2PNetwork implements P2PNetwork {
  @Override
  public Collection<PeerConnection> getPeers() {
    throw new P2PDisabledException("P2P networking disabled.  Peers list unavailable.");
  }

  @Override
  public Stream<DiscoveryPeer> streamDiscoveredPeers() {
    return Stream.empty();
  }

  @Override
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    throw new P2PDisabledException("P2P networking disabled.  Unable to connect to network.");
  }

  @Override
  public void subscribe(final Capability capability, final MessageCallback callback) {}

  @Override
  public void subscribeConnect(final ConnectCallback callback) {}

  @Override
  public void subscribeConnectRequest(final ShouldConnectCallback callback) {}

  @Override
  public void subscribeDisconnect(final DisconnectCallback callback) {}

  @Override
  public boolean addMaintainedConnectionPeer(final Peer peer) {
    throw new P2PDisabledException("P2P networking disabled.  Unable to connect to add peer.");
  }

  @Override
  public boolean removeMaintainedConnectionPeer(final Peer peer) {
    throw new P2PDisabledException("P2P networking disabled.  Unable to remove a connected peer.");
  }

  @Override
  public void stop() {}

  @Override
  public void awaitStop() {}

  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public boolean isP2pEnabled() {
    return false;
  }

  @Override
  public boolean isDiscoveryEnabled() {
    return false;
  }

  @Override
  public boolean isStopped() {
    return true;
  }

  @Override
  public Optional<EnodeURL> getLocalEnode() {
    return Optional.empty();
  }

  @Override
  public void updateNodeRecord() {}

  @Override
  public void close() throws IOException {}

  @Override
  public void start() {}
}
