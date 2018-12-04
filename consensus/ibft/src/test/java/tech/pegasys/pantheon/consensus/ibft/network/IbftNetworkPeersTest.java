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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.ValidatorProvider;
import tech.pegasys.pantheon.crypto.SECP256K1.PublicKey;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection.PeerNotConnected;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftNetworkPeersTest {

  private final List<Address> validators = Lists.newArrayList();
  private final List<PublicKey> publicKeys = Lists.newArrayList();

  private final List<PeerConnection> peerConnections = Lists.newArrayList();

  @Before
  public void setup() {
    for (int i = 0; i < 4; i++) {
      final PublicKey pubKey = PublicKey.create(BigInteger.valueOf(i));
      publicKeys.add(pubKey);

      final PeerInfo peerInfo = mock(PeerInfo.class);
      final PeerConnection peerConnection = mock(PeerConnection.class);
      when(peerConnection.getPeer()).thenReturn(peerInfo);
      when(peerInfo.getNodeId()).thenReturn(pubKey.getEncodedBytes());

      peerConnections.add(peerConnection);
    }
  }

  @Test
  public void onlyValidatorsAreSentAMessage() throws PeerNotConnected {
    // Only add the first Peer's address to the validators.
    validators.add(Util.publicKeyToAddress(publicKeys.get(0)));
    final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidators()).thenReturn(validators);

    final IbftNetworkPeers peers = new IbftNetworkPeers(validatorProvider);
    for (final PeerConnection peer : peerConnections) {
      peers.peerAdded(peer);
    }

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.multicastToValidators(messageToSend);

    verify(peerConnections.get(0), times(1)).sendForProtocol("IBF", messageToSend);
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }

  @Test
  public void doesntSendToValidatorsWhichAreNotDirectlyConnected() throws PeerNotConnected {
    validators.add(Util.publicKeyToAddress(publicKeys.get(0)));

    final ValidatorProvider validatorProvider = mock(ValidatorProvider.class);
    when(validatorProvider.getValidators()).thenReturn(validators);

    final IbftNetworkPeers peers = new IbftNetworkPeers(validatorProvider);

    // only add peer connections 1, 2 & 3, none of which should be invoked.
    Lists.newArrayList(1, 2, 3).forEach(i -> peers.peerAdded(peerConnections.get(i)));

    final MessageData messageToSend = new RawMessage(1, BytesValue.EMPTY);
    peers.multicastToValidators(messageToSend);

    verify(peerConnections.get(0), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(1), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(2), never()).sendForProtocol(any(), any());
    verify(peerConnections.get(3), never()).sendForProtocol(any(), any());
  }
}
