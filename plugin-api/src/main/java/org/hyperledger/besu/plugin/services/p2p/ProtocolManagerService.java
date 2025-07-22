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
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.BesuService;

import java.util.List;

/** Service for managing P2P protocols. */
public interface ProtocolManagerService extends BesuService {

  /**
   * Register a protocol manager for a specific protocol.
   *
   * @param protocolName the name of the protocol
   * @param protocolManager the protocol manager
   */
  void registerProtocolManager(final String protocolName, final ProtocolManager protocolManager);

  /**
   * Unregister a protocol manager for a specific protocol.
   *
   * @param protocolName the name of the protocol
   */
  void unregisterProtocolManager(final String protocolName);

  /**
   * Get all registered protocol names.
   *
   * @return list of registered protocol names
   */
  List<String> getRegisteredProtocols();

  /**
   * Check if a protocol is registered.
   *
   * @param protocolName the protocol name to check
   * @return true if the protocol is registered
   */
  boolean isProtocolRegistered(final String protocolName);

  /** Interface for managing a P2P subprotocol. */
  interface ProtocolManager {

    /**
     * Get the supported protocol name.
     *
     * @return the protocol name
     */
    String getSupportedProtocol();

    /**
     * Get the list of capabilities supported by this manager.
     *
     * @return the list of supported capabilities
     */
    List<Capability> getSupportedCapabilities();

    /**
     * Process a message from a peer.
     *
     * @param cap the capability that corresponds to the message
     * @param message the message from the peer
     */
    void processMessage(final Capability cap, final Message message);

    /**
     * Handle new peer connections.
     *
     * @param peerConnection the new peer connection
     */
    void handleNewConnection(final PeerConnection peerConnection);

    /**
     * Handle peer disconnects.
     *
     * @param peerConnection the connection that is being closed
     * @param disconnectReason the reason given for closing the connection
     * @param initiatedByPeer true if the peer requested to disconnect, false if this node requested
     *     the disconnect
     */
    void handleDisconnect(
        final PeerConnection peerConnection,
        final String disconnectReason,
        final boolean initiatedByPeer);

    /** Stop the protocol manager. */
    void stop();

    /**
     * Get the highest protocol version.
     *
     * @return the highest protocol version
     */
    int getHighestProtocolVersion();
  }
}
