/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.network;

import tech.pegasys.pantheon.consensus.common.VoteTallyCache;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for tracking the network peers which have a connection to this node, then
 * multicasting packets to ONLY the peers which have been identified as being validators.
 */
public class ValidatorPeers implements ValidatorMulticaster, PeerConnectionTracker {

  private static final Logger LOG = LogManager.getLogger();

  private static final String PROTOCOL_NAME = "IBF";

  private final Map<Address, PeerConnection> peerConnections = Maps.newConcurrentMap();
  private final VoteTallyCache voteTallyCache;

  public ValidatorPeers(final VoteTallyCache voteTallyCache) {
    this.voteTallyCache = voteTallyCache;
  }

  @Override
  public void add(final PeerConnection newConnection) {
    final Address peerAddress = newConnection.getPeerInfo().getAddress();
    peerConnections.put(peerAddress, newConnection);
  }

  @Override
  public void remove(final PeerConnection removedConnection) {
    final Address peerAddress = removedConnection.getPeerInfo().getAddress();
    peerConnections.remove(peerAddress);
  }

  @Override
  public void send(final MessageData message) {
    sendMessageToSpecificAddresses(getLatestValidators(), message);
  }

  @Override
  public void send(final MessageData message, final Collection<Address> blackList) {
    final Collection<Address> includedValidators =
        getLatestValidators().stream()
            .filter(a -> !blackList.contains(a))
            .collect(Collectors.toSet());
    sendMessageToSpecificAddresses(includedValidators, message);
  }

  private void sendMessageToSpecificAddresses(
      final Collection<Address> recipients, final MessageData message) {
    LOG.trace(
        "Sending message to peers messageCode={} recipients={}", message.getCode(), recipients);
    recipients.stream()
        .map(peerConnections::get)
        .filter(Objects::nonNull)
        .forEach(
            connection -> {
              try {
                connection.sendForProtocol(PROTOCOL_NAME, message);
              } catch (final PeerNotConnected peerNotConnected) {
                LOG.trace(
                    "Lost connection to a validator. remoteAddress={} peerInfo={}",
                    connection.getRemoteAddress(),
                    connection.getPeerInfo());
              }
            });
  }

  private Collection<Address> getLatestValidators() {
    return voteTallyCache.getVoteTallyAtHead().getValidators();
  }
}
