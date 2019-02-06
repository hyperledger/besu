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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.consensus.ibft.TestHelpers.createFrom;

import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.ibft.BlockTimer;
import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.RoundTimer;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMessageTransmitter;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidatorFactory;
import tech.pegasys.pantheon.consensus.ibft.validation.NewRoundMessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftBlockHeightManagerTest {

  private final KeyPair localNodeKeys = KeyPair.generate();
  private final MessageFactory messageFactory = new MessageFactory(localNodeKeys);
  private final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();

  @Mock private IbftFinalState finalState;
  @Mock private IbftMessageTransmitter messageTransmitter;
  @Mock private RoundChangeManager roundChangeManager;
  @Mock private IbftRoundFactory roundFactory;
  @Mock private Clock clock;
  @Mock private MessageValidatorFactory messageValidatorFactory;
  @Mock private IbftBlockCreator blockCreator;
  @Mock private BlockImporter<IbftContext> blockImporter;
  @Mock private BlockTimer blockTimer;
  @Mock private RoundTimer roundTimer;
  @Mock private NewRoundMessageValidator newRoundPayloadValidator;

  @Captor private ArgumentCaptor<Optional<PreparedRoundArtifacts>> preparedRoundArtifactsCaptor;

  private final List<KeyPair> validatorKeys = Lists.newArrayList();
  private final List<Address> validators = Lists.newArrayList();
  private final List<MessageFactory> validatorMessageFactory = Lists.newArrayList();

  private ProtocolContext<IbftContext> protocolContext;
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private Block createdBlock;

  private void buildCreatedBlock() {

    IbftExtraData extraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]), emptyList(), Optional.empty(), 0, validators);

    headerTestFixture.extraData(extraData.encode());
    final BlockHeader header = headerTestFixture.buildHeader();
    createdBlock = new Block(header, new BlockBody(emptyList(), emptyList()));
  }

  @Before
  public void setup() {
    for (int i = 0; i < 3; i++) {
      final KeyPair key = KeyPair.generate();
      validatorKeys.add(key);
      validators.add(Util.publicKeyToAddress(key.getPublicKey()));
      validatorMessageFactory.add(new MessageFactory(key));
    }

    buildCreatedBlock();

    final MessageValidator messageValidator = mock(MessageValidator.class);
    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validateCommitMessage(any())).thenReturn(true);
    when(messageValidator.validatePrepareMessage(any())).thenReturn(true);
    when(finalState.getTransmitter()).thenReturn(messageTransmitter);
    when(finalState.getBlockTimer()).thenReturn(blockTimer);
    when(finalState.getRoundTimer()).thenReturn(roundTimer);
    when(finalState.getQuorum()).thenReturn(3);
    when(finalState.getMessageFactory()).thenReturn(messageFactory);
    when(blockCreator.createBlock(anyLong())).thenReturn(createdBlock);
    when(newRoundPayloadValidator.validateNewRoundMessage(any())).thenReturn(true);
    when(messageValidatorFactory.createNewRoundValidator(any()))
        .thenReturn(newRoundPayloadValidator);
    when(messageValidatorFactory.createMessageValidator(any())).thenReturn(messageValidator);

    protocolContext =
        new ProtocolContext<>(null, null, new IbftContext(new VoteTally(validators), null));

    // Ensure the created IbftRound has the valid ConsensusRoundIdentifier;
    when(roundFactory.createNewRound(any(), anyInt()))
        .thenAnswer(
            invocation -> {
              final int round = (int) invocation.getArgument(1);
              final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, round);
              final RoundState createdRoundState =
                  new RoundState(roundId, finalState.getQuorum(), messageValidator);
              return new IbftRound(
                  createdRoundState,
                  blockCreator,
                  protocolContext,
                  blockImporter,
                  new Subscribers<>(),
                  localNodeKeys,
                  messageFactory,
                  messageTransmitter);
            });

    when(roundFactory.createNewRoundWithState(any(), any()))
        .thenAnswer(
            invocation -> {
              final RoundState providedRoundState = invocation.getArgument(1);
              return new IbftRound(
                  providedRoundState,
                  blockCreator,
                  protocolContext,
                  blockImporter,
                  new Subscribers<>(),
                  localNodeKeys,
                  messageFactory,
                  messageTransmitter);
            });
  }

  @Test
  public void startsABlockTimerOnStartIfLocalNodeIsTheProoserForRound() {
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(true);

    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();

    verify(blockTimer, times(1)).startTimer(any(), any());
  }

  @Test
  public void onBlockTimerExpiryProposalMessageIsTransmitted() {
    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();

    manager.handleBlockTimerExpiry(roundIdentifier);
    verify(messageTransmitter, times(1)).multicastProposal(eq(roundIdentifier), any());
    verify(messageTransmitter, never()).multicastPrepare(any(), any());
    verify(messageTransmitter, never()).multicastPrepare(any(), any());
  }

  @Test
  public void onRoundChangeReceptionRoundChangeManagerIsInvokedAndNewRoundStarted() {
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);
    final RoundChange roundChange =
        messageFactory.createRoundChange(futureRoundIdentifier, Optional.empty());
    when(roundChangeManager.appendRoundChangeMessage(any()))
        .thenReturn(Optional.of(singletonList(roundChange)));
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(false);

    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();
    verify(roundFactory).createNewRound(any(), eq(0));

    manager.handleRoundChangePayload(roundChange);

    verify(roundChangeManager, times(1)).appendRoundChangeMessage(roundChange);
    verify(roundFactory, times(1))
        .createNewRound(any(), eq(futureRoundIdentifier.getRoundNumber()));
  }

  @Test
  public void onRoundTimerExpiryANewRoundIsCreatedWithAnIncrementedRoundNumber() {
    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();
    verify(roundFactory).createNewRound(any(), eq(0));

    manager.roundExpired(new RoundExpiry(roundIdentifier));
    verify(roundFactory).createNewRound(any(), eq(1));
  }

  @Test
  public void whenSufficientRoundChangesAreReceivedANewRoundMessageIsTransmitted() {
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);
    final RoundChange roundChange =
        messageFactory.createRoundChange(futureRoundIdentifier, Optional.empty());
    final RoundChangeCertificate roundChangCert =
        new RoundChangeCertificate(singletonList(roundChange.getSignedPayload()));

    when(roundChangeManager.appendRoundChangeMessage(any()))
        .thenReturn(Optional.of(singletonList(roundChange)));
    when(finalState.isLocalNodeProposerForRound(any())).thenReturn(true);

    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();

    manager.handleRoundChangePayload(roundChange);

    verify(messageTransmitter, times(1))
        .multicastNewRound(eq(futureRoundIdentifier), eq(roundChangCert), any());
  }

  @Test
  public void messagesForFutureRoundsAreBufferedAndUsedToPreloadNewRoundWhenItIsStarted() {
    final ConsensusRoundIdentifier futureRoundIdentifier = createFrom(roundIdentifier, 0, +2);

    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();

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
                Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 1));

    manager.handlePreparePayload(prepare);
    manager.handleCommitPayload(commit);

    // Force a new round to be started at new round number.
    final NewRound newRound =
        messageFactory.createNewRound(
            futureRoundIdentifier,
            new RoundChangeCertificate(Collections.emptyList()),
            messageFactory.createProposal(futureRoundIdentifier, createdBlock).getSignedPayload());

    manager.handleNewRoundPayload(newRound);

    // Final state sets the Quorum Size to 3, so should send a Prepare and also a commit
    verify(messageTransmitter, times(1)).multicastPrepare(eq(futureRoundIdentifier), any());
    verify(messageTransmitter, times(1)).multicastPrepare(eq(futureRoundIdentifier), any());
  }

  @Test
  public void preparedCertificateIncludedInRoundChangeMessageOnRoundTimeoutExpired() {
    final IbftBlockHeightManager manager =
        new IbftBlockHeightManager(
            headerTestFixture.buildHeader(),
            finalState,
            roundChangeManager,
            roundFactory,
            clock,
            messageValidatorFactory);
    manager.start();
    manager.handleBlockTimerExpiry(roundIdentifier); // Trigger a Proposal creation.

    final Prepare firstPrepare =
        validatorMessageFactory
            .get(0)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    final Prepare secondPrepare =
        validatorMessageFactory
            .get(1)
            .createPrepare(roundIdentifier, Hash.fromHexStringLenient("0"));
    manager.handlePreparePayload(firstPrepare);
    manager.handlePreparePayload(secondPrepare);

    manager.roundExpired(new RoundExpiry(roundIdentifier));

    final ConsensusRoundIdentifier nextRound = createFrom(roundIdentifier, 0, +1);

    verify(messageTransmitter, times(1))
        .multicastRoundChange(eq(nextRound), preparedRoundArtifactsCaptor.capture());
    final Optional<PreparedRoundArtifacts> preparedCert = preparedRoundArtifactsCaptor.getValue();

    assertThat(preparedCert).isNotEmpty();

    assertThat(preparedCert.get().getPreparedCertificate().getPreparePayloads())
        .containsOnly(firstPrepare.getSignedPayload(), secondPrepare.getSignedPayload());
  }
}
