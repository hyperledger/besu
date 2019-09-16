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
package org.hyperledger.besu.consensus.ibft.network;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.crypto.SECP256K1.PublicKey;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection.PeerNotConnected;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.PeerInfo;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorPeersTest {

  private final List<Address> validators = newArrayList();
  private final List<PublicKey> publicKeys = newArrayList();

  private final List<PeerConnection> peerConnections = newArrayList();
  @Mock private VoteTallyCache voteTallyCache;

  @Before
  public void setup() {
    for (int i = 0; i < 4; i++) {
      final PublicKey pubKey = PublicKey.create(BigInteger.valueOf(i));
      publicKeys.add(pubKey);
      final Address address = Util.publicKeyToAddress(pubKey);

      final PeerConnection peerConnection = mockPeerConnection(address);
      peerConnections.add(peerConnection);
    }

    final VoteTally validatorProvider = mock(VoteTally.class);
    when(voteTallyCache.getVoteTallyAtHead()).thenReturn(validatorProvider);
    when(validatorProvider.getValidators()).thenReturn(validators);
  }

  private PeerConnection mockPeerConnection(final Address address) {
    final PeerInfo peerInfo = mock(PeerInfo.class);
    final PeerConnection peerConnection = mock(PeerConnection.class);
    when(peerConnection.getPeerInfo()).thenReturn(peerInfo);
    when(peerInfo.getAddress()).thenReturn(address);
    return peerConnection;
  }

  @Test
  public void onlyValidatorsAreSentAMessage() throws PeerNotConnected {
    // Only add the first Peer's address to the validators.
    validators.add(Util.publicKeyToAddress(publicKeys.get(0)));

    final ValidatorPeers peers = new ValidatorPeers(voteTallyCache);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol("IBF", messageToSend);
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }

  @Test
  public void handlesDuplicateConnection() throws PeerNotConnected {
    final Address peer0Address = peerConnections.get(0).getPeerInfo().getAddress();
    validators.add(peer0Address);
    final PeerConnection duplicatePeer = mockPeerConnection(peer0Address);

    final ValidatorPeers peers = new ValidatorPeers(voteTallyCache);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }
    peers.add(duplicatePeer);

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol("IBF", messageToSend);
    verify(duplicatePeer, times(1)).sendForProtocol("IBF", messageToSend);
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }

  @Test
  public void handlesTransientDuplicateConnection() throws PeerNotConnected {
    final Address peer0Address = peerConnections.get(0).getPeerInfo().getAddress();
    validators.add(peer0Address);
    final PeerConnection duplicatePeer = mockPeerConnection(peer0Address);

    final ValidatorPeers peers = new ValidatorPeers(voteTallyCache);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }
    peers.add(duplicatePeer);
    peers.remove(duplicatePeer);

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol("IBF", messageToSend);
    verify(duplicatePeer, never()).sendForProtocol("IBF", messageToSend);
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }

  @Test
  public void doesntSendToValidatorsWhichAreNotDirectlyConnected() throws PeerNotConnected {
    validators.add(Util.publicKeyToAddress(publicKeys.get(0)));

    final ValidatorPeers peers = new ValidatorPeers(voteTallyCache);

    // only add peer connections 1, 2 & 3, none of which should be invoked.
    newArrayList(1, 2, 3).forEach(i -> peers.add(peerConnections.get(i)));

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.send(messageToSend);

    verify(peerConnections.get(0), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }

  @Test
  public void onlyValidatorsAreSentAMessageNotInExcludes() throws PeerNotConnected {
    // Only add the first Peer's address to the validators.
    final Address validatorAddress = Util.publicKeyToAddress(publicKeys.get(0));
    validators.add(validatorAddress);
    validators.add(Util.publicKeyToAddress(publicKeys.get(1)));

    final ValidatorPeers peers = new ValidatorPeers(voteTallyCache);
    for (final PeerConnection peer : peerConnections) {
      peers.add(peer);
    }

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.send(messageToSend, newArrayList(validatorAddress));

    verify(peerConnections.get(0), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(1), times(1)).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }
}
