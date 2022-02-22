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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.hyperledger.besu.consensus.common.bft.ConsensusRoundHelpers.createFrom;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BlockTimer;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreator;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.validation.FutureRoundProposalMessageValidator;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidator;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidatorFactory;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.util.Subscribers;

import java.math.BigInteger;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QbftBlockHeightManagerTest {

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final MessageFactory messageFactory = new MessageFactory(nodeKey);
  private final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
  private final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

  @Mock private BftFinalState finalState;
  @Mock private QbftMessageTransmitter messageTransmitter;
  @Mock private RoundChangeManager roundChangeManager;
  @Mock private QbftRoundFactory roundFactory;
  @Mock private Clock clock;
  @Mock private MessageValidatorFactory messageValidatorFactory;
  @Mock private BftBlockCreator blockCreator;
  @Mock private BlockImporter blockImporter;
  @Mock private BlockTimer blockTimer;
  @Mock private RoundTimer roundTimer;
  @Mock private FutureRoundProposalMessageValidator futureRoundProposalMessageValidator;
  @Mock private ValidatorMulticaster validatorMulticaster;

  @Captor private ArgumentCaptor<MessageData> sentMessageArgCaptor;

  private final List<Address> validators = Lists.newArrayList();
  private final List<MessageFactory> validatorMessageFactory = Lists.newArrayList();

  private ProtocolContext protocolContext;
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private Block createdBlock;

  private void buildCreatedBlock() {

    final BftExtraData extraData =
        new BftExtraData(Bytes.wrap(new byte[32]), emptyList(), Optional.empty(), 0, validators);

    headerTestFixture.extraData(bftExtraDataCodec.encode(extraData));
    final BlockHeader header = headerTestFixture.buildHeader();
    createdBlock = new Block(header, new BlockBody(emptyList(), emptyList()));
  }

  @Before
  public void setup() {
    for (int i = 0; i < 3; i++) {
      final NodeKey nodeKey = NodeKeyUtils.generate();
      validators.add(Util.publicKeyToAddress(nodeKey.getPublicKey()));
      validatorMessageFactory.add(new MessageFactory(nodeKey));
    }

    buildCreatedBlock();

    final MessageValidator messageValidator = mock(MessageValidator.class);
    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);
    when(finalState.getBlockTimer()).thenReturn(blockTimer);
    when(finalState.getQuorum()).thenReturn(3);
    when(finalState.getValidatorMulticaster()).thenReturn(validatorMulticaster);
    when(blockCreator.createBlock(anyLong())).thenReturn(createdBlock);

    when(futureRoundProposalMessageValidator.validateProposalMessage(any())).thenReturn(true);
    when(messageValidatorFactory.createFutureRoundProposalMessageValidator(anyLong(), any()))
        .thenReturn(futureRoundProposalMessageValidator);
    when(messageValidatorFactory.createMessageValidator(any(), any())).thenReturn(messageValidator);

    protocolContext =
        new ProtocolContext(
            null,
            null,
            setupContextWithBftExtraDataEncoder(
                QbftContext.class, validators, new QbftExtraDataCodec()));

    // Ensure the created QbftRound has the valid ConsensusRoundIdentifier;
    when(roundFactory.createNewRound(any(), anyInt()))
        .thenAnswer(
            invocation -> {
              final int round = invocation.getArgument(1);
              final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, round);
              final RoundState createdRoundState =
                  new RoundState(roundId, finalState.getQuorum(), messageValidator);
              return new QbftRound(
                  createdRoundState,
                  blockCreator,
                  protocolContext,
                  blockImporter,
                  Subscribers.create(),
                  nodeKey,
                  messageFactory,
                  messageTransmitter,
                  roundTimer,
                  bftExtraDataCodec);
            });

    when(roundFactory.createNewRoundWithState(any(), any()))
        .thenAnswer(
            invocation -> {
              final RoundState providedRoundState = invocation.getArgument(1);
              return new QbftRound(
                  providedRoundState,
                  blockCreator,
                  protocolContext,
                  blockImporter,
                  Subscribers.create(),
                  nodeKey,
                  messageFactory,
                  messageTransmitter,
                  roundTimer,
                  bftExtraDataCodec);
            });
  }

  @Test
  public void startsABlockTimerOnStart() {
    new QbftBlockHeightManager(
        headerTestFixture.buildHeader(),
        finalState,
        roundChangeManager,
        roundFactory,
        clock,
        messageValidatorFactory,
        messageFactory);

    verify(blockTimer, times(1)).startTimer(any(), any());
    verify(finalState, never()).isLocalNodeProposerForRound(any());
  }

  @Test
  public void doesNotStartRoundTimerOnStart() {
    new QbftBlockHeightManager(
        headerTestFixture.buildHeader(),
        finalState,
        roundChangeManager,
        roundFactory,
        clock,
        messageValidatorFactory,
        messageFactory);

    verify(roundTimer, never()).startTimer(any());
    verify(finalState, never()).isLocalNodeProposerForRound(any());
  }

  @Test
  public void onBlockTimerExpiryRoundTimerIsStartedAndProposalMessageIsTransmitted() {
    when(finalState.isLocalNodeProposerForRound(roundIdentifier)).thenReturn(true);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    manager.handleBlockTimerExpiry(roundIdentifier);
    verify(messageTransmitter, atLeastOnce())
        .multicastProposal(eq(roundIdentifier), any(), any(), any());
    verify(messageTransmitter, atLeastOnce()).multicastPrepare(eq(roundIdentifier), any());
    verify(roundTimer, times(1)).startTimer(roundIdentifier);
    verify(finalState).isLocalNodeProposerForRound(eq(new ConsensusRoundIdentifier(1, 0)));
  }

  @Test
  public void
      onBlockTimerExpiryForNonProposerRoundTimerIsStartedAndNoProposalMessageIsTransmitted() {
    when(finalState.isLocalNodeProposerForRound(roundIdentifier)).thenReturn(false);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    manager.handleBlockTimerExpiry(roundIdentifier);
    verify(messageTransmitter, never()).multicastProposal(eq(roundIdentifier), any(), any(), any());
    verify(messageTransmitter, never()).multicastPrepare(eq(roundIdentifier), any());
    verify(roundTimer, times(1)).startTimer(roundIdentifier);
    verify(finalState).isLocalNodeProposerForRound(eq(new ConsensusRoundIdentifier(1, 0)));
  }

  @Test
  public void onBlockTimerExpiryDoNothingIfExistingRoundAlreadyStarted() {
    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    // Force a new round to be started at new round number.
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);
    final Proposal futureRoundProposal =
        messageFactory.createProposal(
            futureRoundIdentifier, createdBlock, emptyList(), emptyList());
    manager.handleProposalPayload(futureRoundProposal);
    verify(roundTimer, times(1)).startTimer(futureRoundIdentifier);

    // Nothing should happen for the block timer expiry as we have already created a new round due
    // to the proposal
    manager.handleBlockTimerExpiry(roundIdentifier);

    verify(messageTransmitter, never()).multicastProposal(eq(roundIdentifier), any(), any(), any());
    verify(messageTransmitter, never()).multicastPrepare(eq(roundIdentifier), any());
    verify(finalState, never()).isLocalNodeProposerForRound(any());
  }

  @Test
  public void onRoundChangeReceptionRoundChangeManagerIsInvokedAndNewRoundStarted() {
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);
    final RoundChange roundChange =
        messageFactory.createRoundChange(futureRoundIdentifier, Optional.empty());
    when(roundChangeManager.appendRoundChangeMessage(any()))
        .thenReturn(Optional.of(singletonList(roundChange)));
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(false);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);
    manager.handleBlockTimerExpiry(roundIdentifier);
    verify(roundFactory).createNewRound(any(), eq(0));

    manager.handleRoundChangePayload(roundChange);

    verify(roundChangeManager, times(1)).appendRoundChangeMessage(roundChange);
    verify(roundFactory, times(1))
        .createNewRound(any(), eq(futureRoundIdentifier.getRoundNumber()));
  }

  @Test
  public void onRoundTimerExpiryANewRoundIsCreatedWithAnIncrementedRoundNumber() {
    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);
    manager.handleBlockTimerExpiry(roundIdentifier);
    verify(roundFactory).createNewRound(any(), eq(0));

    manager.roundExpired(new RoundExpiry(roundIdentifier));
    verify(roundFactory).createNewRound(any(), eq(1));
  }

  @Test
  public void whenSufficientRoundChangesAreReceivedAProposalMessageIsTransmitted() {
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);
    final RoundChange roundChange =
        messageFactory.createRoundChange(futureRoundIdentifier, Optional.empty());
    final RoundChangeArtifacts roundChangArtifacts =
        RoundChangeArtifacts.create(singletonList(roundChange));

    when(roundChangeManager.appendRoundChangeMessage(any()))
        .thenReturn(Optional.of(singletonList(roundChange)));
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(true);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);
    reset(messageTransmitter);

    manager.handleRoundChangePayload(roundChange);

    verify(messageTransmitter, times(1))
        .multicastProposal(
            eq(futureRoundIdentifier),
            any(),
            eq(roundChangArtifacts.getRoundChanges()),
            eq(emptyList()));
  }

  @Test
  public void messagesForFutureRoundsAreBufferedAndUsedToPreloadNewRoundWhenItIsStarted() {
    when(finalState.getQuorum()).thenReturn(1);

    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    final Prepare prepare =
        validatorMessageFactory
            .get(0)
            .createPrepare(futureRoundIdentifier, Hash.fromHexStringLenient("0"));
    final Commit commit =
        validatorMessageFactory
            .get(1)
            .createCommit(
                futureRoundIdentifier,
                Hash.fromHexStringLenient("0"),
                SignatureAlgorithmFactory.getInstance()
                    .createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 1));

    manager.handlePreparePayload(prepare);
    manager.handleCommitPayload(commit);

    // Force a new round to be started at new round number.
    final Proposal futureRoundProposal =
        messageFactory.createProposal(
            futureRoundIdentifier, createdBlock, emptyList(), emptyList());

    manager.handleProposalPayload(futureRoundProposal);

    verify(messageTransmitter, times(1)).multicastPrepare(eq(futureRoundIdentifier), any());
    verify(messageTransmitter, times(1)).multicastCommit(eq(futureRoundIdentifier), any(), any());
  }

  @Test
  public void messagesForCurrentRoundAreBufferedAndUsedToPreloadRoundWhenItIsStarted() {
    when(finalState.getQuorum()).thenReturn(1);
    when(finalState.isLocalNodeProposerForRound(roundIdentifier)).thenReturn(true);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    final Prepare prepare =
        validatorMessageFactory
            .get(0)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    final Commit commit =
        validatorMessageFactory
            .get(1)
            .createCommit(
                roundIdentifier,
                Hash.fromHexStringLenient("0"),
                SignatureAlgorithmFactory.getInstance()
                    .createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 1));

    manager.handlePreparePayload(prepare);
    manager.handleCommitPayload(commit);

    // Since we are also a proposer this will also send a proposal
    manager.handleBlockTimerExpiry(roundIdentifier);

    verify(messageTransmitter, times(1)).multicastPrepare(eq(roundIdentifier), any());
    verify(messageTransmitter, times(1)).multicastCommit(eq(roundIdentifier), any(), any());
  }

  @Test
  public void preparedCertificateIncludedInRoundChangeMessageOnRoundTimeoutExpired() {
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(true);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    manager.handleBlockTimerExpiry(roundIdentifier); // Trigger a Proposal creation.

    final Prepare localPrepare =
        messageFactory.createPrepare(roundIdentifier, createdBlock.getHash());

    final Prepare firstPrepare =
        validatorMessageFactory
            .get(0)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    final Prepare secondPrepare =
        validatorMessageFactory
            .get(1)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    final Prepare thirdPrepare =
        validatorMessageFactory
            .get(2)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    manager.handlePreparePayload(firstPrepare);
    manager.handlePreparePayload(secondPrepare);
    manager.handlePreparePayload(thirdPrepare);

    manager.roundExpired(new RoundExpiry(roundIdentifier));

    verify(validatorMulticaster, times(1)).send(sentMessageArgCaptor.capture());
    final MessageData capturedMessageData = sentMessageArgCaptor.getValue();

    assertThat(capturedMessageData).isInstanceOf(RoundChangeMessageData.class);
    final RoundChangeMessageData roundChange = (RoundChangeMessageData) capturedMessageData;

    final RoundChange receivedRoundChange = roundChange.decode(bftExtraDataCodec);

    Assertions.assertThat(receivedRoundChange.getPreparedRoundMetadata()).isNotEmpty();

    assertThat(receivedRoundChange.getPrepares())
        .containsOnly(
            localPrepare.getSignedPayload(),
            firstPrepare.getSignedPayload(),
            secondPrepare.getSignedPayload(),
            thirdPrepare.getSignedPayload());
  }

  @Test
  public void illegalFutureRoundProposalDoesNotTriggerNewRound() {
    when(futureRoundProposalMessageValidator.validateProposalMessage(any())).thenReturn(false);

    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            messageFactory);

    // Force a new round to be started at new round number.
    final Proposal futureRoundProposal =
        messageFactory.createProposal(
            futureRoundIdentifier, createdBlock, emptyList(), emptyList());
    reset(roundFactory); // Discard the existing createNewRound invocation.

    manager.handleProposalPayload(futureRoundProposal);
    verify(roundFactory, never()).createNewRound(any(), anyInt());
  }
}
