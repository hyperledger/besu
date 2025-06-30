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
package org.hyperledger.besu.plugin.data;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

/** 
 * Plugin interface for peer connections.
 * This interface provides plugin access to peer connection functionality.
 */
public interface PeerConnection <C extends Capability> {

  /**
   * Send given data to the connected node.
   *
   * @param message Data to send
   * @param capability Sub-protocol to use
   * @throws PeerNotConnected On attempt to send to a disconnected peer
   */
  void send(Capability capability, Message message) throws PeerNotConnected;

  /**
   * Agreed capabilities between us and the peer.
   *
   * @return a set of shared capabilities between this node and the connected peer
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

  InetSocketAddress getLocalAddress();

  InetSocketAddress getRemoteAddress();

  class PeerNotConnected extends IOException {

    public PeerNotConnected(final String message) {
      super(message);
    }
  }

  /**
   * Get the difference, measured in milliseconds, between the time this connection was initiated
   * and midnight, January 1, 1970 UTC
   *
   * @return the time when this connection was initiated.
   */
  long getInitiatedAt();

  boolean inboundInitiated();

  void setStatusSent();

  void setStatusReceived();

  boolean getStatusExchanged();
}
