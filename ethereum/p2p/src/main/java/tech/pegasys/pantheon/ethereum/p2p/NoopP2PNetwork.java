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
package tech.pegasys.pantheon.ethereum.p2p;

import tech.pegasys.pantheon.ethereum.p2p.api.DisconnectCallback;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.peers.Peer;
import tech.pegasys.pantheon.ethereum.p2p.permissioning.NodeWhitelistController;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class NoopP2PNetwork implements P2PNetwork {
  @Override
  public Collection<PeerConnection> getPeers() {
    throw new P2pDisabledException("P2P networking disabled.  Peers list unavailable.");
  }

  @Override
  public CompletableFuture<PeerConnection> connect(final Peer peer) {
    throw new P2pDisabledException("P2P networking disabled.  Unable to connect to network.");
  }

  @Override
  public void subscribe(final Capability capability, final Consumer<Message> consumer) {}

  @Override
  public void subscribeConnect(final Consumer<PeerConnection> consumer) {}

  @Override
  public void subscribeDisconnect(final DisconnectCallback consumer) {}

  @Override
  public boolean addMaintainConnectionPeer(final Peer peer) {
    throw new P2pDisabledException("P2P networking disabled.  Unable to connect to add peer.");
  }

  @Override
  public void checkMaintainedConnectionPeers() {}

  @Override
  public void stop() {}

  @Override
  public void awaitStop() {}

  @Override
  public InetSocketAddress getDiscoverySocketAddress() {
    throw new P2pDisabledException(
        "P2P networking disabled.  Discovery socket address unavailable.");
  }

  @Override
  public PeerInfo getLocalPeerInfo() {
    throw new P2pDisabledException("P2P networking disabled.  Local peer info unavailable.");
  }

  @Override
  public boolean isListening() {
    return false;
  }

  @Override
  public boolean isP2pEnabled() {
    return false;
  }

  @Override
  public Optional<NodeWhitelistController> getNodeWhitelistController() {
    throw new P2pDisabledException("P2P networking disabled.  Node whitelist unavailable.");
  }

  @Override
  public void close() throws IOException {}

  @Override
  public void run() {}
}
