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
package org.hyperledger.besu.consensus.qbft.core.support;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.common.bft.payload.Payload;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.core.statemachine.PreparedCertificate;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class RoundSpecificPeers {

  private final ValidatorPeer proposer;
  private final Collection<ValidatorPeer> peers;
  private final List<ValidatorPeer> nonProposingPeers;
  private final BftExtraDataCodec bftExtraDataCodec;

  public RoundSpecificPeers(
      final ValidatorPeer proposer,
      final Collection<ValidatorPeer> peers,
      final List<ValidatorPeer> nonProposingPeers,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.proposer = proposer;
    this.peers = peers;
    this.nonProposingPeers = nonProposingPeers;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  public ValidatorPeer getProposer() {
    return proposer;
  }

  public ValidatorPeer getFirstNonProposer() {
    return nonProposingPeers.get(0);
  }

  public void clearReceivedMessages() {
    peers.forEach(ValidatorPeer::clearReceivedMessages);
  }

  public List<SECPSignature> sign(final Hash digest) {
    return peers.stream().map(peer -> peer.getBlockSignature(digest)).collect(Collectors.toList());
  }

  public ValidatorPeer getNonProposing(final int index) {
    return nonProposingPeers.get(index);
  }

  public List<SignedData<RoundChangePayload>> roundChangeForNonProposing(
      final ConsensusRoundIdentifier targetRound) {
    return nonProposingPeers.stream()
        .map(peer -> peer.injectRoundChange(targetRound, empty()).getSignedPayload())
        .collect(Collectors.toList());
  }

  public void commit(final ConsensusRoundIdentifier roundId, final Block block) {
    peers.forEach(peer -> peer.injectCommit(roundId, block));
  }

  public List<SignedData<RoundChangePayload>> roundChange(final ConsensusRoundIdentifier roundId) {
    final List<RoundChange> changes = Lists.newArrayList();

    for (final ValidatorPeer peer : peers) {
      changes.add(peer.injectRoundChange(roundId, empty()));
    }

    return changes.stream().map(RoundChange::getSignedPayload).collect(Collectors.toList());
  }

  public List<SignedData<RoundChangePayload>> createSignedRoundChangePayload(
      final ConsensusRoundIdentifier roundId) {
    return peers.stream()
        .map(p -> p.getMessageFactory().createRoundChange(roundId, empty()).getSignedPayload())
        .collect(Collectors.toList());
  }

  public List<SignedData<RoundChangePayload>> createSignedRoundChangePayload(
      final ConsensusRoundIdentifier roundId, final PreparedCertificate preparedCertificate) {
    return peers.stream()
        .map(
            p ->
                p.getMessageFactory()
                    .createRoundChange(roundId, Optional.of(preparedCertificate))
                    .getSignedPayload())
        .collect(Collectors.toList());
  }

  public void prepareForNonProposing(final ConsensusRoundIdentifier roundId, final Hash hash) {
    nonProposingPeers.forEach(peer -> peer.injectPrepare(roundId, hash));
  }

  public void commitForNonProposing(final ConsensusRoundIdentifier roundId, final Block block) {
    nonProposingPeers.forEach(peer -> peer.injectCommit(roundId, block));
  }

  public void forNonProposing(final Consumer<ValidatorPeer> assertion) {
    nonProposingPeers.forEach(assertion);
  }

  public List<SignedData<PreparePayload>> createSignedPreparePayloadOfAllPeers(
      final ConsensusRoundIdentifier preparedRound, final Hash hash) {
    return peers.stream()
        .map(role -> role.getMessageFactory().createPrepare(preparedRound, hash).getSignedPayload())
        .collect(Collectors.toList());
  }

  public void verifyNoMessagesReceived() {
    peers.forEach(n -> assertThat(n.getReceivedMessages()).isEmpty());
  }

  public void verifyNoMessagesReceivedNonProposing() {
    nonProposingPeers.forEach(n -> assertThat(n.getReceivedMessages()).isEmpty());
  }

  public void verifyNoMessagesReceivedProposer() {
    assertThat(proposer.getReceivedMessages()).isEmpty();
  }

  @SafeVarargs
  public final void verifyMessagesReceivedProposer(final BftMessage<? extends Payload>... msgs) {
    verifyMessagesReceived(ImmutableList.of(proposer), msgs);
  }

  @SafeVarargs
  public final void verifyMessagesReceivedNonProposingExcluding(
      final ValidatorPeer exclude, final BftMessage<? extends Payload>... msgs) {
    final Collection<ValidatorPeer> candidates = Lists.newArrayList(nonProposingPeers);
    candidates.remove(exclude);
    verifyMessagesReceived(candidates, msgs);
  }

  public final void verifyMessagesReceivedNonProposing(final BftMessage<?>... msgs) {
    verifyMessagesReceived(nonProposingPeers, msgs);
  }

  public final void verifyMessagesReceived(final BftMessage<?>... msgs) {
    verifyMessagesReceived(peers, msgs);
  }

  private void verifyMessagesReceived(
      final Collection<ValidatorPeer> candidates, final BftMessage<?>... msgs) {
    candidates.forEach(n -> assertThat(n.getReceivedMessages().size()).isEqualTo(msgs.length));

    List<BftMessage<? extends Payload>> msgList = Arrays.asList(msgs);

    for (int i = 0; i < msgList.size(); i++) {
      final int index = i;
      final BftMessage<? extends Payload> msg = msgList.get(index);
      candidates.forEach(
          n -> {
            final List<MessageData> rxMsgs = n.getReceivedMessages();
            final MessageData rxMsgData = rxMsgs.get(index);
            verifyMessage(rxMsgData, msg);
          });
    }
    candidates.forEach(ValidatorPeer::clearReceivedMessages);
  }

  private void verifyMessage(final MessageData actual, final BftMessage<?> expectedMessage) {
    BftMessage<?> actualSignedPayload = null;

    switch (expectedMessage.getMessageType()) {
      case QbftV1.PROPOSAL:
        actualSignedPayload = ProposalMessageData.fromMessageData(actual).decode(bftExtraDataCodec);
        break;
      case QbftV1.PREPARE:
        actualSignedPayload = PrepareMessageData.fromMessageData(actual).decode();
        break;
      case QbftV1.COMMIT:
        actualSignedPayload = CommitMessageData.fromMessageData(actual).decode();
        break;
      case QbftV1.ROUND_CHANGE:
        actualSignedPayload =
            RoundChangeMessageData.fromMessageData(actual).decode(bftExtraDataCodec);
        break;
      default:
        fail("Illegal QBFTV1 message type.");
        break;
    }
    assertThat(expectedMessage).isEqualToComparingFieldByFieldRecursively(actualSignedPayload);
  }
}
