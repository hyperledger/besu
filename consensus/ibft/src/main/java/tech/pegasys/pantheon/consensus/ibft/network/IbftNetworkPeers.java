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

import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.crypto.SECP256K1.PublicKey;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IbftNetworkPeers {

  private static final Logger LOG = LogManager.getLogger();

  private static final String PROTOCOL_NAME = "IBF";

  private final Map<Address, PeerConnection> peerConnections = Maps.newConcurrentMap();
  private final ValidatorProvider validatorProvider;

  public IbftNetworkPeers(final ValidatorProvider validatorProvider) {
    this.validatorProvider = validatorProvider;
  }

  public void peerAdded(final PeerConnection newConnection) {
    final Address peerAddress = getAddressFrom(newConnection);
    peerConnections.put(peerAddress, newConnection);
  }

  public void peerRemoved(final PeerConnection removedConnection) {
    final Address peerAddress = getAddressFrom(removedConnection);
    peerConnections.remove(peerAddress);
  }

  public void multicastToValidators(final MessageData message) {
    final Collection<Address> validators = validatorProvider.getCurrentValidators();
    sendMessageToSpecificAddresses(validators, message);
  }

  private void sendMessageToSpecificAddresses(
      final Collection<Address> recipients, final MessageData message) {
    recipients
        .stream()
        .map(peerConnections::get)
        .filter(Objects::nonNull)
        .forEach(
            connection -> {
              try {
                connection.sendForProtocol(PROTOCOL_NAME, message);
              } catch (final PeerNotConnected peerNotConnected) {
                LOG.trace("Lost connection to a validator.");
              }
            });
  }

  private Address getAddressFrom(final PeerConnection connection) {
    final BytesValue peerNodeId = connection.getPeer().getNodeId();
    final PublicKey remotePublicKey = PublicKey.create(peerNodeId);
    return Util.publicKeyToAddress(remotePublicKey);
  }
}
