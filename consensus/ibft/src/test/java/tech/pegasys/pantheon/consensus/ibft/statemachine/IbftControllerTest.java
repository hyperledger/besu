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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.CommitMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.NewRoundMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.PrepareMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.ProposalMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.RoundChangeMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftControllerTest {
  @Mock private Blockchain blockChain;
  @Mock private IbftFinalState ibftFinalState;
  @Mock private IbftBlockHeightManagerFactory blockHeightManagerFactory;
  @Mock private BlockHeader blockHeader;
  @Mock private IbftBlockHeightManager blockHeightManager;

  @Mock private SignedData<ProposalPayload> signedProposal;
  @Mock private ProposalMessage proposalMessage;
  @Mock private ProposalPayload proposalPayload;

  @Mock private SignedData<PreparePayload> signedPrepare;
  @Mock private PrepareMessage prepareMessage;
  @Mock private PreparePayload preparePayload;

  @Mock private SignedData<CommitPayload> signedCommit;
  @Mock private CommitMessage commitMessage;
  @Mock private CommitPayload commitPayload;

  @Mock private SignedData<NewRoundPayload> signedNewRound;
  @Mock private NewRoundMessage newRoundMessage;
  @Mock private NewRoundPayload newRoundPayload;

  @Mock private SignedData<RoundChangePayload> signedRoundChange;
  @Mock private RoundChangeMessage roundChangeMessage;
  @Mock private RoundChangePayload roundChangePayload;

  private final Map<Long, List<MessageData>> futureMessages = new HashMap<>();
  private final Address validator = Address.fromHexString("0x0");
  private final Address unknownValidator = Address.fromHexString("0x2");
  private final ConsensusRoundIdentifier futureRoundIdentifier = new ConsensusRoundIdentifier(2, 0);
  private ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(0, 0);
  private IbftController ibftController;

  @Before
  public void setup() {
    ibftController =
        new IbftController(blockChain, ibftFinalState, blockHeightManagerFactory, futureMessages);
    when(blockChain.getChainHeadHeader()).thenReturn(blockHeader);
    when(blockHeightManagerFactory.create(blockHeader)).thenReturn(blockHeightManager);
    when(ibftFinalState.getValidators()).thenReturn(ImmutableList.of(validator));
  }

  @Test
  public void createsNewBlockHeightManagerWhenStarted() {
    ibftController.start();
    assertThat(futureMessages).isEmpty();
    verify(blockHeightManagerFactory).create(blockHeader);
  }

  @Test
  public void startsNewBlockHeightManagerAndReplaysFutureMessages() {
    final ConsensusRoundIdentifier roundIdentifierHeight3 = new ConsensusRoundIdentifier(3, 0);
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(roundIdentifierHeight3, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);
    setupNewRound(roundIdentifierHeight3, validator);

    final List<MessageData> height2Msgs =
        newArrayList(prepareMessage, commitMessage, roundChangeMessage);
    final List<MessageData> height3Msgs = newArrayList(proposalMessage, newRoundMessage);
    futureMessages.put(2L, height2Msgs);
    futureMessages.put(3L, height3Msgs);
    when(blockHeightManager.getChainHeight()).thenReturn(2L);

    ibftController.start();
    assertThat(futureMessages.keySet()).hasSize(1);
    assertThat(futureMessages.get(3L)).isEqualTo(height3Msgs);
    verify(blockHeightManagerFactory).create(blockHeader);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verify(blockHeightManager, never()).handleProposalMessage(signedProposal);
    verify(blockHeightManager).handlePrepareMessage(signedPrepare);
    verify(blockHeightManager).handleCommitMessage(signedCommit);
    verify(blockHeightManager).handleRoundChangeMessage(signedRoundChange);
    verify(blockHeightManager, never()).handleNewRoundMessage(signedNewRound);
  }

  @Test
  public void createsNewBlockHeightManagerAndReplaysFutureMessagesOnNewChainHeadEvent() {
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(futureRoundIdentifier, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);
    setupNewRound(futureRoundIdentifier, validator);

    futureMessages.put(
        2L,
        ImmutableList.of(
            prepareMessage, proposalMessage, commitMessage, roundChangeMessage, newRoundMessage));
    when(blockHeightManager.getChainHeight()).thenReturn(2L);

    final NewChainHead newChainHead = new NewChainHead(blockHeader);
    ibftController.handleNewBlockEvent(newChainHead);

    verify(blockHeightManagerFactory).create(blockHeader);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verify(blockHeightManager).handleProposalMessage(signedProposal);
    verify(blockHeightManager).handlePrepareMessage(signedPrepare);
    verify(blockHeightManager).handleCommitMessage(signedCommit);
    verify(blockHeightManager).handleRoundChangeMessage(signedRoundChange);
    verify(blockHeightManager).handleNewRoundMessage(signedNewRound);
  }

  @Test
  public void handlesRoundExpiry() {
    final RoundExpiry roundExpiry = new RoundExpiry(roundIdentifier);

    ibftController.start();
    ibftController.handleRoundExpiry(roundExpiry);

    verify(blockHeightManager).roundExpired(roundExpiry);
  }

  @Test
  public void handlesBlockTimerExpiry() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(roundIdentifier);

    ibftController.start();
    ibftController.handleBlockTimerExpiry(blockTimerExpiry);

    verify(blockHeightManager).handleBlockTimerExpiry(roundIdentifier);
  }

  @Test
  public void proposalForCurrentHeightIsPassedToBlockHeightManager() {
    setupProposal(roundIdentifier, validator);
    ibftController.start();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(proposalMessage));

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager).handleProposalMessage(signedProposal);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void prepareForCurrentHeightIsPassedToBlockHeightManager() {
    setupPrepare(roundIdentifier, validator);
    ibftController.start();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(prepareMessage));

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager).handlePrepareMessage(signedPrepare);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void commitForCurrentHeightIsPassedToBlockHeightManager() {
    setupCommit(roundIdentifier, validator);
    ibftController.start();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(commitMessage));

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager).handleCommitMessage(signedCommit);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void newRoundForCurrentHeightIsPassedToBlockHeightManager() {
    roundIdentifier = new ConsensusRoundIdentifier(0, 1);
    setupNewRound(roundIdentifier, validator);
    ibftController.start();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(newRoundMessage));

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager).handleNewRoundMessage(signedNewRound);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void roundChangeForCurrentHeightIsPassedToBlockHeightManager() {
    roundIdentifier = new ConsensusRoundIdentifier(0, 1);
    setupRoundChange(roundIdentifier, validator);
    ibftController.start();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(roundChangeMessage));

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager).handleRoundChangeMessage(signedRoundChange);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void proposalForPastHeightIsDiscarded() {
    setupProposal(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(proposalMessage));
  }

  @Test
  public void prepareForPastHeightIsDiscarded() {
    setupPrepare(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(prepareMessage));
  }

  @Test
  public void commitForPastHeightIsDiscarded() {
    setupCommit(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(commitMessage));
  }

  @Test
  public void newRoundForPastHeightIsDiscarded() {
    setupNewRound(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(newRoundMessage));
  }

  @Test
  public void roundChangeForPastHeightIsDiscarded() {
    setupRoundChange(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void roundExpiryForPastHeightIsDiscarded() {
    final RoundExpiry roundExpiry = new RoundExpiry(roundIdentifier);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    ibftController.start();
    ibftController.handleRoundExpiry(roundExpiry);
    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager, never()).roundExpired(any());
  }

  @Test
  public void blockTimerForPastHeightIsDiscarded() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(roundIdentifier);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    ibftController.start();
    ibftController.handleBlockTimerExpiry(blockTimerExpiry);
    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager, never()).handleBlockTimerExpiry(any());
  }

  @Test
  public void proposalForUnknownValidatorIsDiscarded() {
    setupProposal(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(proposalMessage));
  }

  @Test
  public void prepareForUnknownValidatorIsDiscarded() {
    setupPrepare(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(prepareMessage));
  }

  @Test
  public void commitForUnknownValidatorIsDiscarded() {
    setupCommit(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(commitMessage));
  }

  @Test
  public void newRoundForUnknownValidatorIsDiscarded() {
    setupNewRound(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(newRoundMessage));
  }

  @Test
  public void roundChangeForUnknownValidatorIsDiscarded() {
    setupRoundChange(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void proposalForFutureHeightIsBuffered() {
    setupProposal(futureRoundIdentifier, validator);
    final Map<Long, List<MessageData>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(proposalMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(proposalMessage), expectedFutureMsgs);
  }

  @Test
  public void prepareForFutureHeightIsBuffered() {
    setupPrepare(futureRoundIdentifier, validator);
    final Map<Long, List<MessageData>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(prepareMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(prepareMessage), expectedFutureMsgs);
  }

  @Test
  public void commitForFutureHeightIsBuffered() {
    setupCommit(futureRoundIdentifier, validator);
    final Map<Long, List<MessageData>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(commitMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(commitMessage), expectedFutureMsgs);
  }

  @Test
  public void newRoundForFutureHeightIsBuffered() {
    setupNewRound(futureRoundIdentifier, validator);
    final Map<Long, List<MessageData>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(newRoundMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(newRoundMessage), expectedFutureMsgs);
  }

  @Test
  public void roundChangeForFutureHeightIsBuffered() {
    setupRoundChange(futureRoundIdentifier, validator);
    final Map<Long, List<MessageData>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(roundChangeMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(roundChangeMessage), expectedFutureMsgs);
  }

  private void verifyNotHandledAndNoFutureMsgs(final IbftReceivedMessageEvent msg) {
    ibftController.start();
    ibftController.handleMessageEvent(msg);

    assertThat(futureMessages).isEmpty();
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  private void verifyHasFutureMessages(
      final IbftReceivedMessageEvent msg, final Map<Long, List<MessageData>> expectedFutureMsgs) {
    ibftController.start();
    ibftController.handleMessageEvent(msg);

    assertThat(futureMessages).hasSize(expectedFutureMsgs.size());
    assertThat(futureMessages).isEqualTo(expectedFutureMsgs);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verifyNoMoreInteractions(blockHeightManager);
  }

  private void setupProposal(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(signedProposal.getPayload()).thenReturn(proposalPayload);
    when(signedProposal.getSender()).thenReturn(validator);
    when(proposalPayload.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(proposalMessage.getCode()).thenReturn(IbftV2.PROPOSAL);
    when(proposalMessage.decode()).thenReturn(signedProposal);
  }

  private void setupPrepare(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(signedPrepare.getPayload()).thenReturn(preparePayload);
    when(signedPrepare.getSender()).thenReturn(validator);
    when(preparePayload.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(prepareMessage.getCode()).thenReturn(IbftV2.PREPARE);
    when(prepareMessage.decode()).thenReturn(signedPrepare);
  }

  private void setupCommit(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(signedCommit.getPayload()).thenReturn(commitPayload);
    when(signedCommit.getSender()).thenReturn(validator);
    when(commitPayload.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(commitMessage.getCode()).thenReturn(IbftV2.COMMIT);
    when(commitMessage.decode()).thenReturn(signedCommit);
  }

  private void setupNewRound(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(signedNewRound.getPayload()).thenReturn(newRoundPayload);
    when(signedNewRound.getSender()).thenReturn(validator);
    when(newRoundPayload.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(newRoundMessage.getCode()).thenReturn(IbftV2.NEW_ROUND);
    when(newRoundMessage.decode()).thenReturn(signedNewRound);
  }

  private void setupRoundChange(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(signedRoundChange.getPayload()).thenReturn(roundChangePayload);
    when(signedRoundChange.getSender()).thenReturn(validator);
    when(roundChangePayload.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(roundChangeMessage.getCode()).thenReturn(IbftV2.ROUND_CHANGE);
    when(roundChangeMessage.decode()).thenReturn(signedRoundChange);
  }
}
