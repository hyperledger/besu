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
package org.hyperledger.besu.plugin.data.p2p;

import java.net.InetSocketAddress;
import java.util.Set;

/** A P2P connection to another node for plugin use. */
public interface PeerConnection {

  /**
   * Send given data to the connected node.
   *
   * @param message Data to send
   * @param capability Sub-protocol to use
   * @throws PeerNotConnected On attempt to send to a disconnected peer
   */
  void send(Capability capability, MessageData message) throws PeerNotConnected;

  /**
   * Agreed capabilities between us and the peer.
   *
   * @return a list of shared capabilities between this node and the connected peer
   */
  Set<Capability> getAgreedCapabilities();

  /**
   * Returns the agreed capability corresponding to given protocol.
   *
   * @param protocol the name of the protocol
   * @return the agreed capability corresponding to this protocol, returns null if no matching
   *     capability is supported
   */
  default Capability capability(final String protocol) {
    for (final Capability cap : getAgreedCapabilities()) {
      if (cap.getName().equalsIgnoreCase(protocol)) {
        return cap;
      }
    }
    return null;
  }

  /**
   * Sends a message to the peer for the given subprotocol
   *
   * @param protocol the subprotocol name
   * @param message the message to send
   * @throws PeerNotConnected if the peer has disconnected
   */
  default void sendForProtocol(final String protocol, final MessageData message)
      throws PeerNotConnected {
    send(capability(protocol), message);
  }

  /**
   * Data about the peer on the other side of this connection.
   *
   * @return A representation of the remote peer this node is connected to.
   */
  Peer getPeer();

  /**
   * Returns the Peer's Description.
   *
   * @return Peer Description
   */
  PeerInfo getPeerInfo();

  /**
   * Immediately terminate the connection without sending a disconnect message.
   *
   * @param reason the reason for disconnection
   * @param peerInitiated <code>true</code> if and only if the remote peer requested disconnection
   */
  void terminateConnection(DisconnectReason reason, boolean peerInitiated);

  /**
   * Disconnect from this Peer.
   *
   * @param reason Reason for disconnecting
   */
  void disconnect(DisconnectReason reason);

  /**
   * Has this connection been disconnected.
   *
   * @return True if the peer is disconnected
   */
  boolean isDisconnected();

  /**
   * Get local address.
   *
   * @return local address
   */
  InetSocketAddress getLocalAddress();

  /**
   * Get remote address.
   *
   * @return remote address
   */
  InetSocketAddress getRemoteAddress();

  /**
   * Get remote enode URL.
   *
   * @return remote enode URL
   */
  default String getRemoteEnode() {
    return getPeer().getEnodeURL();
  }

  /**
   * Get the difference, measured in milliseconds, between the time this connection was initiated
   * and midnight, January 1, 1970 UTC
   *
   * @return the time when this connection was initiated.
   */
  long getInitiatedAt();

  /**
   * Check if connection was initiated inbound.
   *
   * @return true if inbound initiated
   */
  boolean inboundInitiated();

  /** Set status sent flag. */
  void setStatusSent();

  /** Set status received flag. */
  void setStatusReceived();

  /**
   * Check if status has been exchanged.
   *
   * @return true if status exchanged
   */
  boolean getStatusExchanged();

  /** Exception thrown when attempting to send to a disconnected peer. */
  class PeerNotConnected extends Exception {

    /**
     * Constructs a new PeerNotConnected exception with the specified detail message.
     *
     * @param message the detail message explaining why the peer is not connected
     */
    public PeerNotConnected(final String message) {
      super(message);
    }
  }
}
