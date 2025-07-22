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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.plugin.data.p2p.MessageData;
import org.hyperledger.besu.plugin.data.p2p.PeerConnection;
import org.hyperledger.besu.plugin.services.BesuService;

import java.util.Collection;
import java.util.List;

/** Service for validator network communication. */
public interface ValidatorNetworkService extends BesuService {

  /**
   * Send a message to all validator peers.
   *
   * @param message the message to send
   */
  void sendToValidators(final MessageData message);

  /**
   * Send a message to all validator peers except those in the denylist.
   *
   * @param message the message to send
   * @param denylist addresses to exclude from sending
   */
  void sendToValidators(final MessageData message, final Collection<Address> denylist);

  /**
   * Get all validator peer connections.
   *
   * @return list of validator peer connections
   */
  List<PeerConnection> getValidatorPeerConnections();

  /**
   * Get all validator addresses.
   *
   * @return list of validator addresses
   */
  List<Address> getValidatorAddresses();

  /**
   * Check if a peer is a validator.
   *
   * @param peerConnection the peer connection to check
   * @return true if the peer is a validator
   */
  boolean isValidator(final PeerConnection peerConnection);

  /**
   * Check if an address belongs to a validator.
   *
   * @param address the address to check
   * @return true if the address belongs to a validator
   */
  boolean isValidator(final Address address);

  /**
   * Register a connection tracker for validator peers.
   *
   * @param tracker the connection tracker
   */
  void registerConnectionTracker(final ValidatorConnectionTracker tracker);

  /**
   * Unregister a connection tracker for validator peers.
   *
   * @param tracker the connection tracker
   */
  void unregisterConnectionTracker(final ValidatorConnectionTracker tracker);

  /** Interface for tracking validator peer connections. */
  interface ValidatorConnectionTracker {

    /**
     * Called when a new validator peer connects.
     *
     * @param peerConnection the new peer connection
     */
    void onValidatorConnected(final PeerConnection peerConnection);

    /**
     * Called when a validator peer disconnects.
     *
     * @param peerConnection the disconnected peer connection
     */
    void onValidatorDisconnected(final PeerConnection peerConnection);
  }
}
