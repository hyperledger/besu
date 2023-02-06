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
package org.hyperledger.besu.consensus.common.bft.network;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
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
 * Responsible for tracking the network peers which have a connection to this node, then
 * multicasting packets to ONLY the peers which have been identified as being validators.
 */
public class ValidatorPeers implements ValidatorMulticaster, PeerConnectionTracker {

  private static final Logger LOG = LoggerFactory.getLogger(ValidatorPeers.class);

  // It's possible for multiple connections between peers to exist for brief periods, so map each
  // address to a set of connections
  private final Map<Address, Set<PeerConnection>> connectionsByAddress = new ConcurrentHashMap<>();
  private final ValidatorProvider validatorProvider;
  private final String protocolName;

  /**
   * Instantiates a new Validator peers.
   *
   * @param validatorProvider the validator provider
   * @param protocolName the protocol name
   */
  public ValidatorPeers(final ValidatorProvider validatorProvider, final String protocolName) {
    this.validatorProvider = validatorProvider;
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
    if (connections == null) {
      return;
    }
    connections.remove(removedConnection);
  }

  @Override
  public void send(final MessageData message) {
    sendMessageToSpecificAddresses(getLatestValidators(), message);
  }

  @Override
  public void send(final MessageData message, final Collection<Address> denylist) {
    final Collection<Address> includedValidators =
        getLatestValidators().stream()
            .filter(a -> !denylist.contains(a))
            .collect(Collectors.toSet());
    sendMessageToSpecificAddresses(includedValidators, message);
  }

  private void sendMessageToSpecificAddresses(
      final Collection<Address> recipients, final MessageData message) {
    LOG.trace(
        "Sending message to peers messageCode={} recipients={}", message.getCode(), recipients);
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

  private Collection<Address> getLatestValidators() {
    return validatorProvider.getValidatorsAtHead();
  }
}
