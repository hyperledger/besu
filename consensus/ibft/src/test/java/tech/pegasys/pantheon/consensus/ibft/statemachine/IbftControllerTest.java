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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftGossip;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;

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
  @Mock private BlockHeader chainHeadBlockHeader;
  @Mock private BlockHeader nextBlock;
  @Mock private BlockHeightManager blockHeightManager;

  @Mock private Proposal proposal;
  private Message proposalMessage;
  @Mock private ProposalMessageData proposalMessageData;
  @Mock private ProposalPayload proposalPayload;

  @Mock private Prepare prepare;
  private Message prepareMessage;
  @Mock private PrepareMessageData prepareMessageData;
  @Mock private PreparePayload preparePayload;

  @Mock private Commit commit;
  private Message commitMessage;
  @Mock private CommitMessageData commitMessageData;
  @Mock private CommitPayload commitPayload;

  @Mock private NewRound newRound;
  private Message newRoundMessage;
  @Mock private NewRoundMessageData newRoundMessageData;
  @Mock private NewRoundPayload newRoundPayload;

  @Mock private RoundChange roundChange;
  private Message roundChangeMessage;
  @Mock private RoundChangeMessageData roundChangeMessageData;
  @Mock private RoundChangePayload roundChangePayload;

  private final Map<Long, List<Message>> futureMessages = new HashMap<>();
  private final Address validator = Address.fromHexString("0x0");
  private final Address unknownValidator = Address.fromHexString("0x2");
  private final ConsensusRoundIdentifier futureRoundIdentifier = new ConsensusRoundIdentifier(2, 0);
  private ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(0, 0);
  @Mock private IbftGossip ibftGossip;
  private IbftController ibftController;

  @Before
  public void setup() {
    when(blockChain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(blockHeightManagerFactory.create(any())).thenReturn(blockHeightManager);
    when(ibftFinalState.getValidators()).thenReturn(ImmutableList.of(validator));
    ibftController =
        new IbftController(
            blockChain, ibftFinalState, blockHeightManagerFactory, ibftGossip, futureMessages);

    when(chainHeadBlockHeader.getNumber()).thenReturn(1L);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);

    when(blockHeightManager.getParentBlockHeader()).thenReturn(chainHeadBlockHeader);

    when(nextBlock.getNumber()).thenReturn(2L);

    when(ibftFinalState.isLocalNodeValidator()).thenReturn(true);
  }

  @Test
  public void createsNewBlockHeightManagerWhenStarted() {
    ibftController.start();
    assertThat(futureMessages).isEmpty();
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
  }

  @Test
  public void startsNewBlockHeightManagerAndReplaysFutureMessages() {
    final ConsensusRoundIdentifier roundIdentifierHeight3 = new ConsensusRoundIdentifier(3, 0);
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(roundIdentifierHeight3, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);
    setupNewRound(roundIdentifierHeight3, validator);

    final List<Message> height2Msgs =
        newArrayList(prepareMessage, commitMessage, roundChangeMessage);
    final List<Message> height3Msgs = newArrayList(proposalMessage, newRoundMessage);
    futureMessages.put(2L, height2Msgs);
    futureMessages.put(3L, height3Msgs);
    when(blockHeightManager.getChainHeight()).thenReturn(2L);

    ibftController.start();
    assertThat(futureMessages.keySet()).hasSize(1);
    assertThat(futureMessages.get(3L)).isEqualTo(height3Msgs);
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager).start();
    verify(blockHeightManager, never()).handleProposalPayload(proposal);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
    verify(blockHeightManager, never()).handleNewRoundPayload(newRound);
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

    ibftController.start();
    final NewChainHead newChainHead = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(newChainHead);

    verify(blockHeightManagerFactory).create(nextBlock);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager, times(2)).start(); // once at beginning, and again on newChainHead.
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(ibftGossip).send(proposalMessage);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
    verify(blockHeightManager).handleNewRoundPayload(newRound);
    verify(ibftGossip).send(newRoundMessage);
  }

  @Test
  public void newBlockForCurrentOrPreviousHeightTriggersNoChange() {
    ibftController.start();

    long chainHeadHeight = chainHeadBlockHeader.getNumber();
    when(nextBlock.getNumber()).thenReturn(chainHeadHeight);
    when(nextBlock.getHash()).thenReturn(Hash.ZERO);
    final NewChainHead sameHeightBlock = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(sameHeightBlock);
    verify(blockHeightManagerFactory, times(1)).create(any()); // initial creation
    verify(blockHeightManager, times(1)).start(); // the initial call at start of test.

    when(nextBlock.getNumber()).thenReturn(chainHeadHeight - 1);
    final NewChainHead priorBlock = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(priorBlock);
    verify(blockHeightManagerFactory, times(1)).create(any());
    verify(blockHeightManager, times(1)).start();
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
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(ibftGossip).send(proposalMessage);
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
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
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
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
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
    verify(blockHeightManager).handleNewRoundPayload(newRound);
    verify(ibftGossip).send(newRoundMessage);
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
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
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
    final Map<Long, List<Message>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(proposalMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(proposalMessage), expectedFutureMsgs);
  }

  @Test
  public void prepareForFutureHeightIsBuffered() {
    setupPrepare(futureRoundIdentifier, validator);
    final Map<Long, List<Message>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(prepareMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(prepareMessage), expectedFutureMsgs);
  }

  @Test
  public void commitForFutureHeightIsBuffered() {
    setupCommit(futureRoundIdentifier, validator);
    final Map<Long, List<Message>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(commitMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(commitMessage), expectedFutureMsgs);
  }

  @Test
  public void newRoundForFutureHeightIsBuffered() {
    setupNewRound(futureRoundIdentifier, validator);
    final Map<Long, List<Message>> expectedFutureMsgs =
        ImmutableMap.of(2L, ImmutableList.of(newRoundMessage));
    verifyHasFutureMessages(new IbftReceivedMessageEvent(newRoundMessage), expectedFutureMsgs);
  }

  @Test
  public void roundChangeForFutureHeightIsBuffered() {
    setupRoundChange(futureRoundIdentifier, validator);
    final Map<Long, List<Message>> expectedFutureMsgs =
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
      final IbftReceivedMessageEvent msg, final Map<Long, List<Message>> expectedFutureMsgs) {
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
    when(proposal.getAuthor()).thenReturn(validator);
    when(proposal.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(proposalMessageData.getCode()).thenReturn(IbftV2.PROPOSAL);
    when(proposalMessageData.decode()).thenReturn(proposal);
    proposalMessage = new DefaultMessage(null, proposalMessageData);
  }

  private void setupPrepare(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(prepare.getAuthor()).thenReturn(validator);
    when(prepare.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(prepareMessageData.getCode()).thenReturn(IbftV2.PREPARE);
    when(prepareMessageData.decode()).thenReturn(prepare);
    prepareMessage = new DefaultMessage(null, prepareMessageData);
  }

  private void setupCommit(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(commit.getAuthor()).thenReturn(validator);
    when(commit.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(commitMessageData.getCode()).thenReturn(IbftV2.COMMIT);
    when(commitMessageData.decode()).thenReturn(commit);
    commitMessage = new DefaultMessage(null, commitMessageData);
  }

  private void setupNewRound(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(newRound.getAuthor()).thenReturn(validator);
    when(newRound.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(newRoundMessageData.getCode()).thenReturn(IbftV2.NEW_ROUND);
    when(newRoundMessageData.decode()).thenReturn(newRound);
    newRoundMessage = new DefaultMessage(null, newRoundMessageData);
  }

  private void setupRoundChange(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(roundChange.getAuthor()).thenReturn(validator);
    when(roundChange.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(roundChangeMessageData.getCode()).thenReturn(IbftV2.ROUND_CHANGE);
    when(roundChangeMessageData.decode()).thenReturn(roundChange);
    roundChangeMessage = new DefaultMessage(null, roundChangeMessageData);
  }
}
