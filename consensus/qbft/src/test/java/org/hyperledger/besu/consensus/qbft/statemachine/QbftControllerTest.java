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
package org.hyperledger.besu.consensus.qbft.statemachine;

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

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.EthSynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.QbftGossip;
import org.hyperledger.besu.consensus.qbft.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QbftControllerTest {
  private static final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

  @Mock private Blockchain blockChain;
  @Mock private BftFinalState bftFinalState;
  @Mock private QbftBlockHeightManagerFactory blockHeightManagerFactory;
  @Mock private BlockHeader chainHeadBlockHeader;
  @Mock private BlockHeader nextBlock;
  @Mock private BaseQbftBlockHeightManager blockHeightManager;

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
  private final ConsensusRoundIdentifier futureRoundIdentifier = new ConsensusRoundIdentifier(5, 0);
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(4, 0);
  private final ConsensusRoundIdentifier pastRoundIdentifier = new ConsensusRoundIdentifier(3, 0);
  @Mock private QbftGossip qbftGossip;
  @Mock private FutureMessageBuffer futureMessageBuffer;
  private QbftController qbftController;

  @Before
  public void setup() {
    when(blockChain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(blockChain.getChainHeadBlockNumber()).thenReturn(3L);
    when(blockHeightManagerFactory.create(any())).thenReturn(blockHeightManager);
    when(bftFinalState.getValidators()).thenReturn(ImmutableList.of(validator));

    when(chainHeadBlockHeader.getNumber()).thenReturn(3L);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);

    when(blockHeightManager.getParentBlockHeader()).thenReturn(chainHeadBlockHeader);
    when(blockHeightManager.getChainHeight()).thenReturn(4L); // one great than blockchain

    when(nextBlock.getNumber()).thenReturn(5L);

    when(bftFinalState.isLocalNodeValidator()).thenReturn(true);
    when(messageTracker.hasSeenMessage(any())).thenReturn(false);
  }

  private void constructQbftController() {
    qbftController =
        new QbftController(
            blockChain,
            bftFinalState,
            blockHeightManagerFactory,
            qbftGossip,
            messageTracker,
            futureMessageBuffer,
            mock(EthSynchronizerUpdater.class),
            bftExtraDataCodec);
  }

  @Test
  public void createsNewBlockHeightManagerWhenStarted() {
    constructQbftController();
    verify(blockHeightManagerFactory, never()).create(chainHeadBlockHeader);
    qbftController.start();

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
  }

  @Test
  public void startsNewBlockHeightManagerAndReplaysFutureMessages() {
    final ConsensusRoundIdentifier roundIdentifierHeight6 = new ConsensusRoundIdentifier(6, 0);
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(roundIdentifierHeight6, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);

    final List<Message> height2Msgs =
        newArrayList(prepareMessage, commitMessage, roundChangeMessage);
    when(blockHeightManager.getChainHeight()).thenReturn(5L);
    when(futureMessageBuffer.retrieveMessagesForHeight(5L)).thenReturn(height2Msgs);

    constructQbftController();
    qbftController.start();

    verify(futureMessageBuffer).retrieveMessagesForHeight(5L);
    verify(futureMessageBuffer, never()).retrieveMessagesForHeight(6L);
    verify(blockHeightManagerFactory).create(chainHeadBlockHeader);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(blockHeightManager, never()).handleProposalPayload(proposal);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(qbftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(qbftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(qbftGossip).send(roundChangeMessage);
  }

  @Test
  public void createsNewBlockHeightManagerAndReplaysFutureMessagesOnNewChainHeadEvent() {
    setupPrepare(futureRoundIdentifier, validator);
    setupProposal(futureRoundIdentifier, validator);
    setupCommit(futureRoundIdentifier, validator);
    setupRoundChange(futureRoundIdentifier, validator);

    when(futureMessageBuffer.retrieveMessagesForHeight(5L))
        .thenReturn(
            ImmutableList.of(prepareMessage, proposalMessage, commitMessage, roundChangeMessage))
        .thenReturn(Collections.emptyList());
    when(blockHeightManager.getChainHeight()).thenReturn(5L);

    constructQbftController();
    qbftController.start();
    final NewChainHead newChainHead = new NewChainHead(nextBlock);
    qbftController.handleNewBlockEvent(newChainHead);

    verify(blockHeightManagerFactory).create(nextBlock);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verify(futureMessageBuffer, times(2)).retrieveMessagesForHeight(5L);
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(qbftGossip).send(proposalMessage);
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(qbftGossip).send(prepareMessage);
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(qbftGossip).send(commitMessage);
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(qbftGossip).send(roundChangeMessage);
  }

  @Test
  public void newBlockForCurrentOrPreviousHeightTriggersNoChange() {
    constructQbftController();
    qbftController.start();
    long chainHeadHeight = chainHeadBlockHeader.getNumber();
    when(nextBlock.getNumber()).thenReturn(chainHeadHeight);
    when(nextBlock.getHash()).thenReturn(Hash.ZERO);
    final NewChainHead sameHeightBlock = new NewChainHead(nextBlock);
    qbftController.handleNewBlockEvent(sameHeightBlock);
    verify(blockHeightManagerFactory, times(1)).create(any()); // initial creation

    when(nextBlock.getNumber()).thenReturn(chainHeadHeight - 1);
    final NewChainHead priorBlock = new NewChainHead(nextBlock);
    qbftController.handleNewBlockEvent(priorBlock);
    verify(blockHeightManagerFactory, times(1)).create(any());
  }

  @Test
  public void handlesRoundExpiry() {
    final RoundExpiry roundExpiry = new RoundExpiry(roundIdentifier);

    constructQbftController();
    qbftController.start();
    qbftController.handleRoundExpiry(roundExpiry);

    verify(blockHeightManager).roundExpired(roundExpiry);
  }

  @Test
  public void handlesBlockTimerExpiry() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(roundIdentifier);

    constructQbftController();
    qbftController.start();
    qbftController.handleBlockTimerExpiry(blockTimerExpiry);

    verify(blockHeightManager).handleBlockTimerExpiry(roundIdentifier);
  }

  @Test
  public void proposalForCurrentHeightIsPassedToBlockHeightManager() {
    setupProposal(roundIdentifier, validator);
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(proposalMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleProposalPayload(proposal);
    verify(qbftGossip).send(proposalMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void prepareForCurrentHeightIsPassedToBlockHeightManager() {
    setupPrepare(roundIdentifier, validator);
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(prepareMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handlePreparePayload(prepare);
    verify(qbftGossip).send(prepareMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void commitForCurrentHeightIsPassedToBlockHeightManager() {
    setupCommit(roundIdentifier, validator);
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(commitMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleCommitPayload(commit);
    verify(qbftGossip).send(commitMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void roundChangeForCurrentHeightIsPassedToBlockHeightManager() {
    setupRoundChange(roundIdentifier, validator);
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(roundChangeMessage));

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager).handleRoundChangePayload(roundChange);
    verify(qbftGossip).send(roundChangeMessage);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  @Test
  public void proposalForPastHeightIsDiscarded() {
    setupProposal(pastRoundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(proposalMessage));
  }

  @Test
  public void prepareForPastHeightIsDiscarded() {
    setupPrepare(pastRoundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(prepareMessage));
  }

  @Test
  public void commitForPastHeightIsDiscarded() {
    setupCommit(pastRoundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(commitMessage));
  }

  @Test
  public void roundChangeForPastHeightIsDiscarded() {
    setupRoundChange(pastRoundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void roundExpiryForPastHeightIsDiscarded() {
    final RoundExpiry roundExpiry = new RoundExpiry(pastRoundIdentifier);
    constructQbftController();
    qbftController.start();
    qbftController.handleRoundExpiry(roundExpiry);
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, never()).roundExpired(any());
  }

  @Test
  public void blockTimerForPastHeightIsDiscarded() {
    final BlockTimerExpiry blockTimerExpiry = new BlockTimerExpiry(pastRoundIdentifier);
    constructQbftController();
    qbftController.start();
    qbftController.handleBlockTimerExpiry(blockTimerExpiry);
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, never()).handleBlockTimerExpiry(any());
  }

  @Test
  public void proposalForUnknownValidatorIsDiscarded() {
    setupProposal(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(proposalMessage));
  }

  @Test
  public void prepareForUnknownValidatorIsDiscarded() {
    setupPrepare(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(prepareMessage));
  }

  @Test
  public void commitForUnknownValidatorIsDiscarded() {
    setupCommit(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(commitMessage));
  }

  @Test
  public void roundChangeForUnknownValidatorIsDiscarded() {
    setupRoundChange(roundIdentifier, unknownValidator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(roundChangeMessage));
  }

  @Test
  public void proposalForFutureHeightIsBuffered() {
    setupProposal(futureRoundIdentifier, validator);
    verifyHasFutureMessages(futureRoundIdentifier.getSequenceNumber(), proposalMessage);
  }

  @Test
  public void prepareForFutureHeightIsBuffered() {
    setupPrepare(futureRoundIdentifier, validator);
    verifyHasFutureMessages(futureRoundIdentifier.getSequenceNumber(), prepareMessage);
  }

  @Test
  public void commitForFutureHeightIsBuffered() {
    setupCommit(futureRoundIdentifier, validator);
    verifyHasFutureMessages(futureRoundIdentifier.getSequenceNumber(), commitMessage);
  }

  @Test
  public void roundChangeForFutureHeightIsBuffered() {
    setupRoundChange(futureRoundIdentifier, validator);
    verifyHasFutureMessages(futureRoundIdentifier.getSequenceNumber(), roundChangeMessage);
  }

  @Test
  public void duplicatedMessagesAreNotProcessed() {
    when(messageTracker.hasSeenMessage(proposalMessageData)).thenReturn(true);
    setupProposal(roundIdentifier, validator);
    verifyNotHandledAndNoFutureMsgs(new BftReceivedMessageEvent(proposalMessage));
    verify(messageTracker, never()).addSeenMessage(proposalMessageData);
  }

  @Test
  public void uniqueMessagesAreAddedAsSeen() {
    when(messageTracker.hasSeenMessage(proposalMessageData)).thenReturn(false);
    setupProposal(roundIdentifier, validator);
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(proposalMessage));

    verify(messageTracker).addSeenMessage(proposalMessageData);
  }

  @Test
  public void messagesWhichAreAboveHeightManagerButBelowBlockChainLengthAreDiscarded() {
    // NOTE: for this to occur, the system would need to be synchronising - i.e. blockchain is
    // moving up faster than qbft loop is handling NewBlock messages
    final long blockchainLength = 10L;
    final long blockHeightManagerTargettingBlock = 6L;
    final long messageHeight = 8L;
    setupProposal(new ConsensusRoundIdentifier(messageHeight, 0), validator);

    when(blockChain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(blockChain.getChainHeadBlockNumber()).thenReturn(blockchainLength);
    when(blockHeightManagerFactory.create(any())).thenReturn(blockHeightManager);
    when(blockHeightManager.getChainHeight()).thenReturn(blockHeightManagerTargettingBlock);

    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(proposalMessage));
    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, never()).handleProposalPayload(any());
  }

  private void verifyNotHandledAndNoFutureMsgs(final BftReceivedMessageEvent msg) {
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(msg);

    verify(futureMessageBuffer, never()).addMessage(anyLong(), any());
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  private void verifyHasFutureMessages(final long msgHeight, final Message message) {
    constructQbftController();
    qbftController.start();
    qbftController.handleMessageEvent(new BftReceivedMessageEvent(message));

    verify(futureMessageBuffer).addMessage(msgHeight, message);
    verify(blockHeightManager, atLeastOnce()).getChainHeight();
    verifyNoMoreInteractions(blockHeightManager);
  }

  private void setupProposal(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(proposal.getAuthor()).thenReturn(validator);
    when(proposal.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(proposalMessageData.getCode()).thenReturn(QbftV1.PROPOSAL);
    when(proposalMessageData.decode(bftExtraDataCodec)).thenReturn(proposal);
    proposalMessage = new DefaultMessage(null, proposalMessageData);
  }

  private void setupPrepare(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(prepare.getAuthor()).thenReturn(validator);
    when(prepare.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(prepareMessageData.getCode()).thenReturn(QbftV1.PREPARE);
    when(prepareMessageData.decode()).thenReturn(prepare);
    prepareMessage = new DefaultMessage(null, prepareMessageData);
  }

  private void setupCommit(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(commit.getAuthor()).thenReturn(validator);
    when(commit.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(commitMessageData.getCode()).thenReturn(QbftV1.COMMIT);
    when(commitMessageData.decode()).thenReturn(commit);
    commitMessage = new DefaultMessage(null, commitMessageData);
  }

  private void setupRoundChange(
      final ConsensusRoundIdentifier roundIdentifier, final Address validator) {
    when(roundChange.getAuthor()).thenReturn(validator);
    when(roundChange.getRoundIdentifier()).thenReturn(roundIdentifier);
    when(roundChangeMessageData.getCode()).thenReturn(QbftV1.ROUND_CHANGE);
    when(roundChangeMessageData.decode(bftExtraDataCodec)).thenReturn(roundChange);
    roundChangeMessage = new DefaultMessage(null, roundChangeMessageData);
  }
}
