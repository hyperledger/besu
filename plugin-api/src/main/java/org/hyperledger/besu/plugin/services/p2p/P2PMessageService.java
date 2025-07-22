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
package org.hyperledger.besu.plugin.services.p2p;

import org.hyperledger.besu.plugin.data.p2p.Capability;
import org.hyperledger.besu.plugin.data.p2p.Message;
import org.hyperledger.besu.plugin.data.p2p.MessageData;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.BesuService;

import java.util.List;
import java.util.function.Consumer;

/** Service for P2P messaging capabilities. */
public interface P2PMessageService extends BesuService {

  /**
   * Register a message handler for a specific protocol.
   *
   * @param protocolName the name of the protocol
   * @param messageHandler the handler for messages of this protocol
   */
  void registerMessageHandler(final String protocolName, final Consumer<Message> messageHandler);

  /**
   * Unregister a message handler for a specific protocol.
   *
   * @param protocolName the name of the protocol
   */
  void unregisterMessageHandler(final String protocolName);

  /**
   * Send a message to a specific peer.
   *
   * @param peerConnection the peer connection to send to
   * @param capability the capability to use
   * @param message the message to send
   * @throws PeerConnection.PeerNotConnected if the peer is not connected
   */
  void sendMessage(
      final PeerConnection peerConnection, final Capability capability, final MessageData message)
      throws PeerConnection.PeerNotConnected;

  /**
   * Send a message to a specific peer using protocol name.
   *
   * @param peerConnection the peer connection to send to
   * @param protocolName the protocol name
   * @param message the message to send
   * @throws PeerConnection.PeerNotConnected if the peer is not connected
   */
  void sendMessageForProtocol(
      final PeerConnection peerConnection, final String protocolName, final MessageData message)
      throws PeerConnection.PeerNotConnected;

  /**
   * Get all connected peer connections.
   *
   * @return list of connected peer connections
   */
  List<PeerConnection> getPeerConnections();

  /**
   * Get supported capabilities.
   *
   * @return list of supported capabilities
   */
  List<Capability> getSupportedCapabilities();
}
