/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.common.bft.network;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for tracking all network peers which have a connection to this node, then
 * multicasting packets to all connected peers.
 */
public class Peers implements PeerMulticaster, PeerConnectionTracker {
  private static final Logger LOG = LoggerFactory.getLogger(Peers.class);

  /**
   * The connectionsByAddress map is a mapping of peer addresses to the set of PeerConnections
   * associated with that address. This allows for multiple connections to the same peer address.
   */
  protected final Map<Address, Set<PeerConnection>> connectionsByAddress =
      new ConcurrentHashMap<>();

  /**
   * The protocolName is the name of the protocol being used for communication. This is used to
   * identify the protocol when sending messages.
   */
  protected final String protocolName;

  /**
   * Constructor for Peers.
   *
   * @param protocolName the name of the protocol
   */
  public Peers(final String protocolName) {
    this.protocolName = protocolName;
  }

  @Override
  public void add(final PeerConnection newConnection) {
    final Address peerAddress = newConnection.getPeerInfo().getAddress();
    final Set<PeerConnection> connections =
        connectionsByAddress.computeIfAbsent(
            peerAddress, (k) -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
    connections.add(newConnection);
  }

  @Override
  public void remove(final PeerConnection removedConnection) {
    final Address peerAddress = removedConnection.getPeerInfo().getAddress();
    final Set<PeerConnection> connections = connectionsByAddress.get(peerAddress);
    if (connections != null) {
      connections.remove(removedConnection);
      if (connections.isEmpty()) {
        connectionsByAddress.remove(peerAddress);
      }
    }
  }

  @Override
  public void send(final MessageData message) {
    send(message, Collections.emptyList());
  }

  @Override
  public void send(final MessageData message, final Collection<Address> denylist) {
    final Collection<Address> allowList =
        connectionsByAddress.keySet().stream()
            .filter(address -> !denylist.contains(address))
            .collect(Collectors.toSet());

    sendMessageToSpecificAddresses(allowList, message);
  }

  /**
   * Send a message to a specific set of addresses.
   *
   * @param recipients the recipients
   * @param message the message
   */
  protected void sendMessageToSpecificAddresses(
      final Collection<Address> recipients, final MessageData message) {
    LOG.trace(
        "Sending message to peers messageCode={} recipients={} protocol={}",
        message.getCode(),
        recipients,
        protocolName);
    recipients.stream()
        .map(connectionsByAddress::get)
        .filter(Objects::nonNull)
        .flatMap(Set::stream)
        .forEach(
            connection -> {
              try {
                connection.sendForProtocol(protocolName, message);
              } catch (final PeerNotConnected peerNotConnected) {
                LOG.trace(
                    "Lost connection to a validator. remoteAddress={} peerInfo={}",
                    connection.getRemoteAddress(),
                    connection.getPeerInfo());
              }
            });
  }
}
