/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.services;

import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.plugin.data.p2p.Peer;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.p2p.P2PService;

import java.util.Collection;

/**
 * Default implementation of the {@link P2PService} interface, providing methods to manage P2P
 * network discovery and peer connections.
 */
public class P2PServiceImpl implements P2PService {

  private final P2PNetwork p2PNetwork;

  /**
   * Creates a new P2PServiceImpl.
   *
   * @param p2PNetwork the P2P network
   */
  public P2PServiceImpl(final P2PNetwork p2PNetwork) {
    this.p2PNetwork = p2PNetwork;
  }

  /** Enables P2P discovery. */
  @Override
  public void enableDiscovery() {
    p2PNetwork.start();
  }

  /** Disables P2P discovery. */
  @Override
  public void disableDiscovery() {
    p2PNetwork.stop();
  }

  /**
   * Returns the number of currently connected peers.
   *
   * @return the count of connected peers
   */
  @Override
  public int getPeerCount() {
    return p2PNetwork.getPeerCount();
  }

  /**
   * Returns the current peer connections.
   *
   * @return an immutable snapshot of {@link PeerConnection} objects
   */
  @Override
  public Collection<? extends PeerConnection> getPeerConnections() {
    return p2PNetwork.getPeers();
  }

  /**
   * Returns the set of peers that the node attempts to maintain a connection with.
   *
   * @return maintained peers
   */
  @Override
  public Collection<? extends Peer> getMaintainedConnectionPeers() {
    return p2PNetwork.getMaintainedConnectionPeers();
  }

  /**
   * Subscribes to connection events.
   *
   * @param connectionListener the listener to receive connection events
   */
  @Override
  public void subscribeConnect(final ConnectionListener connectionListener) {
    p2PNetwork.subscribeConnect(connectionListener::onConnect);
  }

  /**
   * Subscribes to disconnection events.
   *
   * @param networkSubscriber the subscriber to receive disconnection events
   */
  @Override
  public void subscribeDisconnect(final DisconnectionListener networkSubscriber) {
    p2PNetwork.subscribeDisconnect(
        (peerConnection, disconnectReason, initiatedByPeer) ->
            networkSubscriber.onDisconnect(
                peerConnection,
                disconnectReason.getCode(),
                disconnectReason.getMessage(),
                initiatedByPeer));
  }

  /**
   * Subscribes to messages on a specific capability.
   *
   * @param capability the capability to subscribe to
   * @param networkSubscriber the subscriber to receive messages for the specified capability
   */
  @Override
  public void subscribeMessage(
      final org.hyperledger.besu.plugin.data.p2p.Capability capability,
      final MessageListener networkSubscriber) {
    final Capability wireCap = Capability.create(capability.getName(), capability.getVersion());
    p2PNetwork.subscribe(wireCap, networkSubscriber::onMessage);
  }
}
