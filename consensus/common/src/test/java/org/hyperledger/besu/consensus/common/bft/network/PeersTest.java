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

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hyperledger.besu.consensus.common.validator.ValidatorProvider;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.math.BigInteger;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PeersTest {

  public static final String PROTOCOL_NAME = "BFT";
  private final List<Address> validators = newArrayList();
  private final List<SECPPublicKey> publicKeys = newArrayList();

  private final List<PeerConnection> peerConnections = newArrayList();
  @Mock ValidatorProvider validatorProvider;

  @BeforeEach
  public void setup() {
    for (int i = 0; i < 4; i++) {
      final SECPPublicKey pubKey =
          SignatureAlgorithmFactory.getInstance().createPublicKey(BigInteger.valueOf(i));
      publicKeys.add(pubKey);
      final Address address = Util.publicKeyToAddress(pubKey);

      final PeerConnection peerConnection = mockPeerConnection(address);
      peerConnections.add(peerConnection);
    }
  }

  private PeerConnection mockPeerConnection(final Address address) {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    final PeerConnection peerConnection = mock(PeerConnection.class);
    lenient().when(peerConnection.getPeerInfo()).thenReturn(peerInfo);
    lenient().when(peerInfo.getAddress()).thenReturn(address);
    return peerConnection;
  }

  @Test
  public void allPeersAreSentAMessage() throws PeerNotConnected {

    final Peers peers = new Peers(PROTOCOL_NAME);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }

    final MessageData messageToSend = new RawMessage(1, Bytes.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(1), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(2), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(3), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
  }

  @Test
  public void handlesDuplicateConnection() throws PeerNotConnected {
    final Address peer0Address = peerConnections.get(0).getPeerInfo().getAddress();
    validators.add(peer0Address);
    final PeerConnection duplicatePeer = mockPeerConnection(peer0Address);

    final Peers peers = new Peers(PROTOCOL_NAME);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }
    peers.add(duplicatePeer);

    final MessageData messageToSend = new RawMessage(1, Bytes.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(duplicatePeer, times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(1), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(2), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(3), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
  }

  @Test
  public void handlesTransientDuplicateConnection() throws PeerNotConnected {
    final Address peer0Address = peerConnections.get(0).getPeerInfo().getAddress();
    validators.add(peer0Address);
    final PeerConnection duplicatePeer = mockPeerConnection(peer0Address);

    final Peers peers = new Peers(PROTOCOL_NAME);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }
    peers.add(duplicatePeer);
    peers.remove(duplicatePeer);

    final MessageData messageToSend = new RawMessage(1, Bytes.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(duplicatePeer, never()).sendForProtocol("BFT", messageToSend);
    verify(peerConnections.get(1), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(2), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
    verify(peerConnections.get(3), times(1)).sendForProtocol(PROTOCOL_NAME, messageToSend);
  }

  @Test
  public void onlyPeersAreSentAMessageNotInExcludes() throws PeerNotConnected {

    final Address peerAddress = Util.publicKeyToAddress(publicKeys.get(0));

    final Peers peers = new Peers(PROTOCOL_NAME);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }

    final MessageData messageToSend = new RawMessage(1, Bytes.EMPTY);
    peers.send(messageToSend, newArrayList(peerAddress));

    verify(peerConnections.get(0), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(1), times(1)).sendForProtocol(any(), any());
    verify(peerConnections.get(2), times(1)).sendForProtocol(any(), any());
    verify(peerConnections.get(3), times(1)).sendForProtocol(any(), any());
  }
}
