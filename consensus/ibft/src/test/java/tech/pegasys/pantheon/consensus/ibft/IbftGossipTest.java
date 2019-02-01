/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft;

import static com.google.common.collect.Lists.newArrayList;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.IbftMessage;
import tech.pegasys.pantheon.consensus.ibft.network.MockPeerFactory;
import tech.pegasys.pantheon.consensus.ibft.network.ValidatorMulticaster;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.PeerConnection;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;

import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftGossipTest {
  private IbftGossip ibftGossip;
  @Mock private ValidatorMulticaster validatorMulticaster;
  private PeerConnection peerConnection;
  private static final Address senderAddress = AddressHelpers.ofValue(9);

  @Before
  public void setup() {
    ibftGossip = new IbftGossip(validatorMulticaster);
    peerConnection = MockPeerFactory.create(senderAddress);
  }

  private <P extends IbftMessage<?>> void assertRebroadcastToAllExceptSignerAndSender(
      final Function<KeyPair, P> createPayload, final Function<P, MessageData> createMessageData) {
    final KeyPair keypair = KeyPair.generate();
    final P payload = createPayload.apply(keypair);
    final MessageData messageData = createMessageData.apply(payload);
    final Message message = new DefaultMessage(peerConnection, messageData);

    ibftGossip.send(message);
    verify(validatorMulticaster)
        .send(messageData, newArrayList(senderAddress, payload.getAuthor()));
  }

  @Test
  public void assertRebroadcastsProposalToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedProposalPayload, ProposalMessageData::create);
  }

  @Test
  public void assertRebroadcastsRoundChangeToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedRoundChangePayload, RoundChangeMessageData::create);
  }

  @Test
  public void assertRebroadcastsNewRoundToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedNewRoundPayload, NewRoundMessageData::create);
  }
}
