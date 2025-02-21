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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
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
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.qbft.core.QbftBlockTestFixture;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCreator;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockImporter;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockInterface;
import org.hyperledger.besu.consensus.qbft.core.types.QbftContext;
import org.hyperledger.besu.consensus.qbft.core.types.QbftExtraDataProvider;
import org.hyperledger.besu.consensus.qbft.core.types.QbftFinalState;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSpec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftValidatorProvider;
import org.hyperledger.besu.consensus.qbft.core.validation.FutureRoundProposalMessageValidator;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidator;
import org.hyperledger.besu.consensus.qbft.core.validation.MessageValidatorFactory;
import org.hyperledger.besu.consensus.qbft.core.validation.QbftBlockHeaderTestFixture;
import org.hyperledger.besu.consensus.qbft.core.validation.RoundChangeMessageValidator;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class QbftBlockHeightManagerTest {

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final QbftBlockHeaderTestFixture headerTestFixture = new QbftBlockHeaderTestFixture();
  private MessageFactory messageFactory;

  @Mock private QbftFinalState finalState;
  @Mock private QbftMessageTransmitter messageTransmitter;
  @Mock private RoundChangeManager roundChangeManager;
  @Mock private QbftRoundFactory roundFactory;
  @Mock private Clock clock;
  @Mock private MessageValidatorFactory messageValidatorFactory;
  @Mock private QbftBlockCreator blockCreator;
  @Mock private BlockTimer blockTimer;
  @Mock private RoundTimer roundTimer;
  @Mock private FutureRoundProposalMessageValidator futureRoundProposalMessageValidator;
  @Mock private ValidatorMulticaster validatorMulticaster;
  @Mock private ProtocolContext protocolContext;
  @Mock private QbftProtocolSchedule protocolSchedule;
  @Mock private QbftBlockHeader parentHeader;
  @Mock private BftExtraDataCodec bftExtraDataCodec;
  @Mock private QbftBlockCodec blockEncoder;
  @Mock private QbftExtraDataProvider qbftExtraDataProvider;
  @Mock private QbftBlockInterface blockInterface;
  @Mock private QbftValidatorProvider validatorProvider;
  @Mock private QbftProtocolSpec protocolSpec;
  @Mock private QbftBlockImporter blockImporter;

  @Captor private ArgumentCaptor<MessageData> sentMessageArgCaptor;

  private final List<Address> validators = Lists.newArrayList();
  private final List<MessageFactory> validatorMessageFactory = Lists.newArrayList();

  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private final QbftBlock createdBlock = new QbftBlockTestFixture().build();

  @BeforeEach
  public void setup() {
    messageFactory = new MessageFactory(nodeKey, blockEncoder);

    for (int i = 0; i < 3; i++) {
      final NodeKey nodeKey = NodeKeyUtils.generate();
      validators.add(Util.publicKeyToAddress(nodeKey.getPublicKey()));
      validatorMessageFactory.add(new MessageFactory(nodeKey, blockEncoder));
    }

    final MessageValidator messageValidator = mock(MessageValidator.class);
    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);
    when(finalState.getBlockTimer()).thenReturn(blockTimer);
    when(finalState.getRoundTimer()).thenReturn(roundTimer);
    when(finalState.getQuorum()).thenReturn(3);
    when(finalState.getValidatorMulticaster()).thenReturn(validatorMulticaster);
    when(finalState.getClock()).thenReturn(clock);
    when(blockCreator.createBlock(anyLong(), any())).thenReturn(createdBlock);

    QbftContext qbftContext = new QbftContext(validatorProvider, blockInterface);
    when(protocolContext.getConsensusContext(QbftContext.class)).thenReturn(qbftContext);

    when(futureRoundProposalMessageValidator.validateProposalMessage(any())).thenReturn(true);
    when(messageValidatorFactory.createFutureRoundProposalMessageValidator(anyLong(), any()))
        .thenReturn(futureRoundProposalMessageValidator);
    when(messageValidatorFactory.createMessageValidator(any(), any())).thenReturn(messageValidator);

    // Ensure the created QbftRound has the valid ConsensusRoundIdentifier;
    when(roundFactory.createNewRound(any(), anyInt()))
        .thenAnswer(
            invocation -> {
              final int round = invocation.getArgument(1);
              final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, round);
              final RoundState createdRoundState = new RoundState(roundId, 3, messageValidator);
              return new QbftRound(
                  createdRoundState,
                  blockCreator,
                  protocolContext,
                  protocolSchedule,
                  Subscribers.create(),
                  nodeKey,
                  messageFactory,
                  messageTransmitter,
                  roundTimer,
                  bftExtraDataCodec,
                  qbftExtraDataProvider,
                  parentHeader);
            });

    when(roundFactory.createNewRoundWithState(any(), any()))
        .thenAnswer(
            invocation -> {
              final RoundState providedRoundState = invocation.getArgument(1);
              return new QbftRound(
                  providedRoundState,
                  blockCreator,
                  protocolContext,
                  protocolSchedule,
                  Subscribers.create(),
                  nodeKey,
                  messageFactory,
                  messageTransmitter,
                  roundTimer,
                  bftExtraDataCodec,
                  qbftExtraDataProvider,
                  parentHeader);
            });

    when(qbftExtraDataProvider.getExtraData(any()))
        .thenReturn(
            new BftExtraData(
                Bytes.wrap(new byte[32]), emptyList(), Optional.empty(), 0, validators));
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(
            new BftExtraData(
                Bytes.wrap(new byte[32]), emptyList(), Optional.empty(), 0, validators));
    when(bftExtraDataCodec.encode(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
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
    when(blockTimer.checkEmptyBlockExpired(any(), eq(0L))).thenReturn(true);
    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(0), any()))
        .thenReturn(createdBlock);

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
    when(blockTimer.checkEmptyBlockExpired(any(), eq(0L))).thenReturn(true);

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

    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(2), any()))
        .thenReturn(createdBlock);

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
    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(2), any()))
        .thenReturn(createdBlock);

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
    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(2), any()))
        .thenReturn(createdBlock);
    when(blockCreator.createSealedBlock(any(), any(), anyInt(), any())).thenReturn(createdBlock);
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);

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
    when(blockTimer.checkEmptyBlockExpired(any(), eq(0L))).thenReturn(true);
    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(0), any()))
        .thenReturn(createdBlock);
    when(blockCreator.createSealedBlock(any(), any(), anyInt(), any())).thenReturn(createdBlock);
    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);
    when(protocolSpec.getBlockImporter()).thenReturn(blockImporter);

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
    when(blockTimer.checkEmptyBlockExpired(any(), eq(0L))).thenReturn(true);
    when(blockInterface.replaceRoundInBlock(eq(createdBlock), eq(0), any()))
        .thenReturn(createdBlock);
    when(blockEncoder.readFrom(any(), any())).thenReturn(createdBlock);

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

    final RoundChange receivedRoundChange = roundChange.decode(blockEncoder);

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

  @Test
  public void checkOnlyEmptyBlockPeriodSecondsIsInvokedForBlocksWithNoTransactions() {
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

    verify(blockTimer, times(0)).getEmptyBlockPeriodSeconds();
    verify(blockTimer, times(0)).getBlockPeriodSeconds();
  }

  @Test
  public void roundChangeTriggeredUponReceivingFPlusOneRoundChanges() {
    final ConsensusRoundIdentifier futureRoundIdentifier1 = createFrom(roundIdentifier, 0, +2);
    final ConsensusRoundIdentifier futureRoundIdentifier2 = createFrom(roundIdentifier, 0, +3);

    final RoundChange roundChange1 =
        validatorMessageFactory.get(0).createRoundChange(futureRoundIdentifier1, Optional.empty());
    final RoundChange roundChange2 =
        validatorMessageFactory.get(1).createRoundChange(futureRoundIdentifier2, Optional.empty());

    RoundChangeMessageValidator roundChangeMessageValidator =
        mock(RoundChangeMessageValidator.class);
    when(roundChangeMessageValidator.validate(any())).thenReturn(true);

    // Instantiate the real RoundChangeManager
    final RoundChangeManager roundChangeManager =
        new RoundChangeManager(3, 2, roundChangeMessageValidator, validators.get(2));

    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(false);

    final QbftBlockHeightManager manager =
        new QbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory,
            validatorMessageFactory.get(2),
            true); // Enable early round change

    manager.handleRoundChangePayload(roundChange1);
    manager.handleRoundChangePayload(roundChange2);

    verify(roundFactory, times(1))
        .createNewRound(any(), eq(futureRoundIdentifier1.getRoundNumber()));
  }
}
