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
package tech.pegasys.pantheon.consensus.ibft.support;

import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.IbftMessage;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.payload.Payload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.statemachine.TerminatedRoundArtefacts;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class RoundSpecificPeers {

  private final ValidatorPeer proposer;
  private final Collection<ValidatorPeer> peers;
  private final List<ValidatorPeer> nonProposingPeers;

  public RoundSpecificPeers(
      final ValidatorPeer proposer,
      final Collection<ValidatorPeer> peers,
      final List<ValidatorPeer> nonProposingPeers) {
    this.proposer = proposer;
    this.peers = peers;
    this.nonProposingPeers = nonProposingPeers;
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

  public List<Signature> sign(final Hash digest) {
    return peers.stream().map(peer -> peer.getBlockSignature(digest)).collect(Collectors.toList());
  }

  public ValidatorPeer getNonProposing(final int index) {
    return nonProposingPeers.get(index);
  }

  public List<SignedData<RoundChangePayload>> roundChangeForNonProposing(
      final ConsensusRoundIdentifier targetRound) {
    return nonProposingPeers
        .stream()
        .map(peer -> peer.injectRoundChange(targetRound, empty()).getSignedPayload())
        .collect(Collectors.toList());
  }

  public void commit(final ConsensusRoundIdentifier roundId, final Hash hash) {
    peers.forEach(peer -> peer.injectCommit(roundId, hash));
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
    return peers
        .stream()
        .map(p -> p.getMessageFactory().createRoundChange(roundId, empty()).getSignedPayload())
        .collect(Collectors.toList());
  }

  public List<SignedData<RoundChangePayload>> createSignedRoundChangePayload(
      final ConsensusRoundIdentifier roundId,
      final TerminatedRoundArtefacts terminatedRoundArtefacts) {
    return peers
        .stream()
        .map(
            p ->
                p.getMessageFactory()
                    .createRoundChange(roundId, Optional.of(terminatedRoundArtefacts))
                    .getSignedPayload())
        .collect(Collectors.toList());
  }

  public void prepareForNonProposing(final ConsensusRoundIdentifier roundId, final Hash hash) {
    nonProposingPeers.forEach(peer -> peer.injectPrepare(roundId, hash));
  }

  public void commitForNonProposing(final ConsensusRoundIdentifier roundId, final Hash hash) {
    nonProposingPeers.forEach(peer -> peer.injectCommit(roundId, hash));
  }

  public Collection<SignedData<PreparePayload>> createSignedPreparePayloadOfNonProposing(
      final ConsensusRoundIdentifier preparedRound, final Hash hash) {
    return nonProposingPeers
        .stream()
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
  public final void verifyMessagesReceivedProposer(final IbftMessage<? extends Payload>... msgs) {
    verifyMessagesReceived(ImmutableList.of(proposer), msgs);
  }

  @SafeVarargs
  public final void verifyMessagesReceivedNonPropsingExcluding(
      final ValidatorPeer exclude, final IbftMessage<? extends Payload>... msgs) {
    final Collection<ValidatorPeer> candidates = Lists.newArrayList(nonProposingPeers);
    candidates.remove(exclude);
    verifyMessagesReceived(candidates, msgs);
  }

  public final void verifyMessagesReceivedNonPropsing(final IbftMessage<?>... msgs) {
    verifyMessagesReceived(nonProposingPeers, msgs);
  }

  public final void verifyMessagesReceived(final IbftMessage<?>... msgs) {
    verifyMessagesReceived(peers, msgs);
  }

  private void verifyMessagesReceived(
      final Collection<ValidatorPeer> candidates, final IbftMessage<?>... msgs) {
    candidates.forEach(n -> assertThat(n.getReceivedMessages().size()).isEqualTo(msgs.length));

    List<IbftMessage<? extends Payload>> msgList = Arrays.asList(msgs);

    for (int i = 0; i < msgList.size(); i++) {
      final int index = i;
      final IbftMessage<? extends Payload> msg = msgList.get(index);
      candidates.forEach(
          n -> {
            final List<MessageData> rxMsgs = n.getReceivedMessages();
            final MessageData rxMsgData = rxMsgs.get(index);
            verifyMessage(rxMsgData, msg);
          });
    }
    candidates.forEach(ValidatorPeer::clearReceivedMessages);
  }

  private void verifyMessage(final MessageData actual, final IbftMessage<?> expectedMessage) {
    IbftMessage<?> actualSignedPayload = null;

    switch (expectedMessage.getMessageType()) {
      case IbftV2.PROPOSAL:
        actualSignedPayload = ProposalMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.PREPARE:
        actualSignedPayload = PrepareMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.COMMIT:
        actualSignedPayload = CommitMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.NEW_ROUND:
        actualSignedPayload = NewRoundMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.ROUND_CHANGE:
        actualSignedPayload = RoundChangeMessageData.fromMessageData(actual).decode();
        break;
      default:
        fail("Illegal IBFTV2 message type.");
        break;
    }
    assertThat(expectedMessage).isEqualToComparingFieldByFieldRecursively(actualSignedPayload);
  }
}
