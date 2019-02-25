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

import static org.assertj.core.util.Lists.emptyList;
import static org.assertj.core.util.Lists.newArrayList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.EthSynchronizerUpdater;
import tech.pegasys.pantheon.consensus.ibft.IbftGossip;
import tech.pegasys.pantheon.consensus.ibft.MessageTracker;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.IbftReceivedMessageEvent;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;

import java.util.List;

import com.google.common.collect.ImmutableList;
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

  @Mock private Prepare prepare;
  private Message prepareMessage;
  @Mock private PrepareMessageData prepareMessageData;

  @Mock private Commit commit;
  private Message commitMessage;
  @Mock private CommitMessageData commitMessageData;

  @Mock private RoundChange roundChange;
  private Message roundChangeMessage;
  @Mock private RoundChangeMessageData roundChangeMessageData;

  @Mock private MessageTracker messageTracker;
  private final Address validator = Address.fromHexString("0x0");
  private final Address unknownValidator = Address.fromHexString("0x2");
  private final ConsensusRoundIdentifier futureRoundIdentifier = new ConsensusRoundIdentifier(2, 0);
  private ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(0, 0);
  @Mock private IbftGossip ibftGossip;
  @Mock private FutureMessageBuffer futureMessageBuffer;
  private IbftController ibftController;

  @Before
  public void setup() {
    when(blockChain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(blockHeightManagerFactory.create(any())).thenReturn(blockHeightManager);
    when(ibftFinalState.getValidators()).thenReturn(ImmutableList.of(validator));

    when(chainHeadBlockHeader.getNumber()).thenReturn(1L);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);

    when(blockHeightManager.getParentBlockHeader()).thenReturn(chainHeadBlockHeader);

    when(nextBlock.getNumber()).thenReturn(2L);

    when(ibftFinalState.isLocalNodeValidator()).thenReturn(true);
    when(messageTracker.hasSeenMessage(any())).thenReturn(false);
  }

  private void constructIbftController() {
    ibftController =
        new IbftController(
            blockChain,
            ibftFinalState,
            blockHeightManagerFactory,
            ibftGossip,
            messageTracker,
            futureMessageBuffer,
            mock(EthSynchronizerUpdater.class));
  }

  @Test
  public void createsNewBlockHeightManagerWhenStarted() {
    constructIbftController();
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
  }

  @Test
  public void startsNewBlockHeightManagerAndReplaysFutureMessages() {
    final ConsensusRoundIdentifier roundIdentifierHeight3 = new ConsensusRoundIdentifier(3, 0);
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(roundIdentifierHeight3, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);

    final List<Message> height2Msgs =
        newArrayList(prepareMessage, commitMessage, roundChangeMessage);
    when(blockHeightManager.getChainHeight()).thenReturn(2L);
    when(futureMessageBuffer.retrieveMessagesForHeight(2L)).thenReturn(height2Msgs);

    constructIbftController();

    verify(futureMessageBuffer).retrieveMessagesForHeight(2L);
    verify(futureMessageBuffer, never()).retrieveMessagesForHeight(3L);
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager, never()).handleProposalPayload(proposal);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
  }

  @Test
  public void createsNewBlockHeightManagerAndReplaysFutureMessagesOnNewChainHeadEvent() {
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(futureRoundIdentifier, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);

    when(futureMessageBuffer.retrieveMessagesForHeight(2L))
        .thenReturn(
            ImmutableList.of(prepareMessage, proposalMessage, commitMessage, roundChangeMessage))
        .thenReturn(emptyList());
    when(blockHeightManager.getChainHeight()).thenReturn(2L);

    constructIbftController();
    final NewChainHead newChainHead = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(newChainHead);

    verify(blockHeightManagerFactory).create(nextBlock);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(futureMessageBuffer, times(2)).retrieveMessagesForHeight(2L);
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(ibftGossip).send(proposalMessage);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
  }

  @Test
  public void newBlockForCurrentOrPreviousHeightTriggersNoChange() {
    constructIbftController();
    long chainHeadHeight = chainHeadBlockHeader.getNumber();
    when(nextBlock.getNumber()).thenReturn(chainHeadHeight);
    when(nextBlock.getHash()).thenReturn(Hash.ZERO);
    final NewChainHead sameHeightBlock = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(sameHeightBlock);
    verify(blockHeightManagerFactory, times(1)).create(any()); // initial creation

    when(nextBlock.getNumber()).thenReturn(chainHeadHeight - 1);
    final NewChainHead priorBlock = new NewChainHead(nextBlock);
    ibftController.handleNewBlockEvent(priorBlock);
    verify(blockHeightManagerFactory, times(1)).create(any());
  }

  @Test
  public void handlesRoundExpiry() {
    final RoundExpiry roundExpiry = new RoundExpiry(roundIdentifier);

    constructIbftController();
    ibftController.handleRoundExpiry(roundExpiry);

    verify(blockHeightManager).roundExpired(roundExpiry);
  }

  @Test
  public void handlesBlockTimerExpiry() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(roundIdentifier);

    constructIbftController();
    ibftController.handleBlockTimerExpiry(blockTimerExpiry);

    verify(blockHeightManager).handleBlockTimerExpiry(roundIdentifier);
  }

  @Test
  public void proposalForCurrentHeightIsPassedToBlockHeightManager() {
    setupProposal(roundIdentifier, validator);
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(proposalMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(ibftGossip).send(proposalMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void prepareForCurrentHeightIsPassedToBlockHeightManager() {
    setupPrepare(roundIdentifier, validator);
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(prepareMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(ibftGossip).send(prepareMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void commitForCurrentHeightIsPassedToBlockHeightManager() {
    setupCommit(roundIdentifier, validator);
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(commitMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(ibftGossip).send(commitMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void roundChangeForCurrentHeightIsPassedToBlockHeightManager() {
    roundIdentifier = new ConsensusRoundIdentifier(0, 1);
    setupRoundChange(roundIdentifier, validator);
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(roundChangeMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(ibftGossip).send(roundChangeMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
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
  public void roundChangeForPastHeightIsDiscarded() {
    setupRoundChange(roundIdentifier, validator);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void roundExpiryForPastHeightIsDiscarded() {
    final RoundExpiry roundExpiry = new RoundExpiry(roundIdentifier);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    constructIbftController();
    ibftController.handleRoundExpiry(roundExpiry);
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, never()).roundExpired(any());
  }

  @Test
  public void blockTimerForPastHeightIsDiscarded() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(roundIdentifier);
    when(blockHeightManager.getChainHeight()).thenReturn(1L);
    constructIbftController();
    ibftController.handleBlockTimerExpiry(blockTimerExpiry);
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
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
  public void roundChangeForUnknownValidatorIsDiscarded() {
    setupRoundChange(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void proposalForFutureHeightIsBuffered() {
    setupProposal(futureRoundIdentifier, validator);
    verifyHasFutureMessages(2L, proposalMessage);
  }

  @Test
  public void prepareForFutureHeightIsBuffered() {
    setupPrepare(futureRoundIdentifier, validator);
    verifyHasFutureMessages(2L, prepareMessage);
  }

  @Test
  public void commitForFutureHeightIsBuffered() {
    setupCommit(futureRoundIdentifier, validator);
    verifyHasFutureMessages(2L, commitMessage);
  }

  @Test
  public void roundChangeForFutureHeightIsBuffered() {
    setupRoundChange(futureRoundIdentifier, validator);
    verifyHasFutureMessages(2L, roundChangeMessage);
  }

  @Test
  public void duplicatedMessagesAreNotProcessed() {
    when(messageTracker.hasSeenMessage(proposalMessageData)).thenReturn(true);
    setupProposal(roundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new IbftReceivedMessageEvent(proposalMessage));
    verify(messageTracker, never()).addSeenMessage(proposalMessageData);
  }

  @Test
  public void uniqueMessagesAreAddedAsSeen() {
    when(messageTracker.hasSeenMessage(proposalMessageData)).thenReturn(false);
    setupProposal(roundIdentifier, validator);
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(proposalMessage));

    verify(messageTracker).addSeenMessage(proposalMessageData);
  }

  private void verifyNotHandledAndNoFutureMsgs(final IbftReceivedMessageEvent msg) {
    constructIbftController();
    ibftController.handleMessageEvent(msg);

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  private void verifyHasFutureMessages(final long msgHeight, final Message message) {
    constructIbftController();
    ibftController.handleMessageEvent(new IbftReceivedMessageEvent(message));

    verify(futureMessageBuffer).addMessage(msgHeight, message);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
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

  private void setupRoundChange(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(roundChange.getAuthor()).thenReturn(validator);
    when(roundChange.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(roundChangeMessageData.getCode()).thenReturn(IbftV2.ROUND_CHANGE);
    when(roundChangeMessageData.decode()).thenReturn(roundChange);
    roundChangeMessage = new DefaultMessage(null, roundChangeMessageData);
  }
}
