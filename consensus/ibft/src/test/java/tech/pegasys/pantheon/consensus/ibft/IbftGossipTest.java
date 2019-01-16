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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.network.MockPeerFactory;
import tech.pegasys.pantheon.consensus.ibft.network.ValidatorMulticaster;
import tech.pegasys.pantheon.consensus.ibft.payload.Payload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
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
    ibftGossip = new IbftGossip(validatorMulticaster, 10);
    peerConnection = MockPeerFactory.create(senderAddress);
  }

  private <P extends Payload> void assertRebroadcastToAllExceptSignerAndSender(
      final Function<KeyPair, SignedData<P>> createPayload,
      final Function<SignedData<P>, MessageData> createMessageData) {
    final KeyPair keypair = KeyPair.generate();
    final SignedData<P> payload = createPayload.apply(keypair);
    final MessageData messageData = createMessageData.apply(payload);
    final Message message = new DefaultMessage(peerConnection, messageData);

    final boolean gossipResult = ibftGossip.gossipMessage(message);
    assertThat(gossipResult).isTrue();
    verify(validatorMulticaster)
        .send(messageData, newArrayList(senderAddress, payload.getSender()));
  }

  private <P extends Payload> void assertRebroadcastOnlyOnce(
      final Function<KeyPair, SignedData<P>> createPayload,
      final Function<SignedData<P>, MessageData> createMessageData) {
    final KeyPair keypair = KeyPair.generate();
    final SignedData<P> payload = createPayload.apply(keypair);
    final MessageData messageData = createMessageData.apply(payload);
    final Message message = new DefaultMessage(peerConnection, messageData);

    final boolean gossip1Result = ibftGossip.gossipMessage(message);
    final boolean gossip2Result = ibftGossip.gossipMessage(message);
    assertThat(gossip1Result).isTrue();
    assertThat(gossip2Result).isFalse();
    verify(validatorMulticaster, times(1))
        .send(messageData, newArrayList(senderAddress, payload.getSender()));
  }

  @Test
  public void assertRebroadcastsProposalToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedProposalPayload, ProposalMessageData::create);
  }

  @Test
  public void assertRebroadcastsProposalOnlyOnce() {
    assertRebroadcastOnlyOnce(
        TestHelpers::createSignedProposalPayload, ProposalMessageData::create);
  }

  @Test
  public void assertRebroadcastsPrepareToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedPreparePayload, PrepareMessageData::create);
  }

  @Test
  public void assertRebroadcastsPrepareOnlyOnce() {
    assertRebroadcastOnlyOnce(TestHelpers::createSignedPreparePayload, PrepareMessageData::create);
  }

  @Test
  public void assertRebroadcastsCommitToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedCommitPayload, CommitMessageData::create);
  }

  @Test
  public void assertRebroadcastsCommitOnlyOnce() {
    assertRebroadcastOnlyOnce(TestHelpers::createSignedCommitPayload, CommitMessageData::create);
  }

  @Test
  public void assertRebroadcastsRoundChangeToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedRoundChangePayload, RoundChangeMessageData::create);
  }

  @Test
  public void assertRebroadcastsRoundChangeOnlyOnce() {
    assertRebroadcastOnlyOnce(
        TestHelpers::createSignedRoundChangePayload, RoundChangeMessageData::create);
  }

  @Test
  public void assertRebroadcastsNewRoundToAllExceptSignerAndSender() {
    assertRebroadcastToAllExceptSignerAndSender(
        TestHelpers::createSignedNewRoundPayload, NewRoundMessageData::create);
  }

  @Test
  public void assertRebroadcastsNewRoundOnlyOnce() {
    assertRebroadcastOnlyOnce(
        TestHelpers::createSignedNewRoundPayload, NewRoundMessageData::create);
  }

  @Test
  public void evictMessageRecordAtCapacity() {
    final KeyPair keypair = KeyPair.generate();
    final SignedData<ProposalPayload> payload =
        TestHelpers.createSignedProposalPayloadWithRound(keypair, 0);
    final MessageData messageData = ProposalMessageData.create(payload);
    final Message message = new DefaultMessage(peerConnection, messageData);
    final boolean gossip1Result = ibftGossip.gossipMessage(message);
    final boolean gossip2Result = ibftGossip.gossipMessage(message);
    assertThat(gossip1Result).isTrue();
    assertThat(gossip2Result).isFalse();
    verify(validatorMulticaster, times(1))
        .send(messageData, newArrayList(senderAddress, payload.getSender()));

    for (int i = 1; i <= 9; i++) {
      final SignedData<ProposalPayload> nextPayload =
          TestHelpers.createSignedProposalPayloadWithRound(keypair, i);
      final MessageData nextMessageData = ProposalMessageData.create(nextPayload);
      final Message nextMessage = new DefaultMessage(peerConnection, nextMessageData);
      final boolean nextGossipResult = ibftGossip.gossipMessage(nextMessage);
      assertThat(nextGossipResult).isTrue();
    }

    final boolean gossip3Result = ibftGossip.gossipMessage(message);
    assertThat(gossip3Result).isFalse();
    verify(validatorMulticaster, times(1))
        .send(messageData, newArrayList(senderAddress, payload.getSender()));

    {
      final SignedData<ProposalPayload> nextPayload =
          TestHelpers.createSignedProposalPayloadWithRound(keypair, 10);
      final MessageData nextMessageData = ProposalMessageData.create(nextPayload);
      final Message nextMessage = new DefaultMessage(peerConnection, nextMessageData);
      final boolean nextGossipResult = ibftGossip.gossipMessage(nextMessage);
      assertThat(nextGossipResult).isTrue();
    }

    final boolean gossip4Result = ibftGossip.gossipMessage(message);
    assertThat(gossip4Result).isTrue();
    verify(validatorMulticaster, times(2))
        .send(messageData, newArrayList(senderAddress, payload.getSender()));

    final boolean gossip5Result = ibftGossip.gossipMessage(message);
    assertThat(gossip5Result).isFalse();
    verify(validatorMulticaster, times(2))
        .send(messageData, newArrayList(senderAddress, payload.getSender()));
  }
}
