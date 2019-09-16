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
package org.hyperledger.besu.consensus.ibft.statemachine;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.IbftContextBuilder.setupContextWithValidators;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.consensus.ibft.RoundTimer;
import org.hyperledger.besu.consensus.ibft.blockcreation.IbftBlockCreator;
import org.hyperledger.besu.consensus.ibft.network.IbftMessageTransmitter;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.payload.RoundChangeCertificate;
import org.hyperledger.besu.consensus.ibft.validation.MessageValidator;
import org.hyperledger.besu.crypto.SECP256K1;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.crypto.SECP256K1.Signature;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.util.Subscribers;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IbftRoundTest {

  private final KeyPair localNodeKeys = KeyPair.generate();
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private final MessageFactory messageFactory = new MessageFactory(localNodeKeys);
  private final Subscribers<MinedBlockObserver> subscribers = Subscribers.create();
  private ProtocolContext<IbftContext> protocolContext;

  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private BlockImporter<IbftContext> blockImporter;
  @Mock private IbftMessageTransmitter transmitter;
  @Mock private MinedBlockObserver minedBlockObserver;
  @Mock private IbftBlockCreator blockCreator;
  @Mock private MessageValidator messageValidator;
  @Mock private RoundTimer roundTimer;

  @Captor private ArgumentCaptor<Block> blockCaptor;

  private Block proposedBlock;
  private IbftExtraData proposedExtraData;

  private final Signature remoteCommitSeal =
      Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 1);

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext<>(
            blockChain, worldStateArchive, setupContextWithValidators(emptyList()));

    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);

    proposedExtraData =
        new IbftExtraData(BytesValue.wrap(new byte[32]), emptyList(), empty(), 0, emptyList());
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    headerTestFixture.extraData(proposedExtraData.encode());
    headerTestFixture.number(1);

    final BlockHeader header = headerTestFixture.buildHeader();
    proposedBlock = new Block(header, new BlockBody(emptyList(), emptyList()));

    when(blockCreator.createBlock(anyLong())).thenReturn(proposedBlock);

    when(blockImporter.importBlock(any(), any(), any())).thenReturn(true);

    subscribers.subscribe(minedBlockObserver);
  }

  @Test
  public void onConstructionRoundTimerIsStarted() {
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);
    new IbftRound(
        roundState,
        blockCreator,
        protocolContext,
        blockImporter,
        subscribers,
        localNodeKeys,
        messageFactory,
        transmitter,
        roundTimer);
    verify(roundTimer, times(1)).startTimer(roundIdentifier);
  }

  @Test
  public void onReceptionOfValidProposalSendsAPrepareToNetworkPeers() {
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    round.handleProposalMessage(
        messageFactory.createProposal(roundIdentifier, proposedBlock, Optional.empty()));
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, never()).multicastCommit(any(), any(), any());
  }

  @Test
  public void sendsAProposalWhenRequested() {
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1))
        .multicastProposal(roundIdentifier, proposedBlock, Optional.empty());
    verify(transmitter, never()).multicastPrepare(any(), any());
    verify(transmitter, never()).multicastCommit(any(), any(), any());
  }

  @Test
  public void singleValidatorImportBlocksImmediatelyOnProposalCreation() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);
    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1))
        .multicastProposal(roundIdentifier, proposedBlock, Optional.empty());
    verify(transmitter, never()).multicastPrepare(any(), any());
    verify(transmitter, times(1)).multicastCommit(any(), any(), any());
    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void twoValidatorNetworkSendsPrepareOnProposalReceptionThenSendsCommitOnCommitReceive() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    final Hash commitSealHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(
            proposedBlock.getHeader(), proposedExtraData);
    final Signature localCommitSeal = SECP256K1.sign(commitSealHash, localNodeKeys);

    // Receive Proposal Message
    round.handleProposalMessage(
        messageFactory.createProposal(roundIdentifier, proposedBlock, Optional.empty()));
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, times(1))
        .multicastCommit(roundIdentifier, proposedBlock.getHash(), localCommitSeal);
    verify(blockImporter, never()).importBlock(any(), any(), any());

    // Receive Commit Message

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    // Should import block when both commit seals are available.
    final ArgumentCaptor<Block> capturedBlock = ArgumentCaptor.forClass(Block.class);
    verify(blockImporter, times(1)).importBlock(any(), capturedBlock.capture(), any());

    // Ensure imported block contains both commit seals.
    final IbftExtraData importedExtraData =
        IbftExtraData.decode(capturedBlock.getValue().getHeader());
    assertThat(importedExtraData.getSeals()).containsOnly(remoteCommitSeal, localCommitSeal);
  }

  @Test
  public void localNodeProposesToNetworkOfTwoValidatorsImportsOnReceptionOfCommitFromPeer() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    final Hash commitSealHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(
            proposedBlock.getHeader(), proposedExtraData);
    final Signature localCommitSeal = SECP256K1.sign(commitSealHash, localNodeKeys);

    round.createAndSendProposalMessage(15);
    verify(transmitter, never()).multicastCommit(any(), any(), any());
    verify(blockImporter, never()).importBlock(any(), any(), any());

    round.handlePrepareMessage(
        messageFactory.createPrepare(roundIdentifier, proposedBlock.getHash()));

    verify(transmitter, times(1))
        .multicastCommit(roundIdentifier, proposedBlock.getHash(), localCommitSeal);
    verify(blockImporter, never()).importBlock(any(), any(), any());

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));
    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void aProposalWithAnewBlockIsSentUponReceptionOfARoundChangeWithNoCertificate() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    final RoundChangeCertificate roundChangeCertificate = new RoundChangeCertificate(emptyList());

    round.startRoundWith(new RoundChangeArtifacts(empty(), emptyList()), 15);
    verify(transmitter, times(1))
        .multicastProposal(eq(roundIdentifier), any(), eq(Optional.of(roundChangeCertificate)));
  }

  @Test
  public void aProposalMessageWithTheSameBlockIsSentUponReceptionOfARoundChangeWithCertificate() {
    final ConsensusRoundIdentifier priorRoundChange = new ConsensusRoundIdentifier(1, 0);
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    final RoundChangeArtifacts roundChangeArtifacts =
        RoundChangeArtifacts.create(
            Collections.singletonList(
                messageFactory.createRoundChange(
                    roundIdentifier,
                    Optional.of(
                        new PreparedRoundArtifacts(
                            messageFactory.createProposal(
                                priorRoundChange, proposedBlock, Optional.empty()),
                            emptyList())))));

    // NOTE: IbftRound assumes the prepare's are valid

    round.startRoundWith(roundChangeArtifacts, 15);
    verify(transmitter, times(1))
        .multicastProposal(
            eq(roundIdentifier),
            blockCaptor.capture(),
            eq(Optional.of(roundChangeArtifacts.getRoundChangeCertificate())));

    final IbftExtraData proposedExtraData =
        IbftExtraData.decode(blockCaptor.getValue().getHeader());
    assertThat(proposedExtraData.getRound()).isEqualTo(roundIdentifier.getRoundNumber());

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory.createPrepare(roundIdentifier, proposedBlock.getHash()));
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void creatingNewBlockFromEmptyPreparedCertificateUpdatesInternalState() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    final RoundChangeArtifacts roundChangeArtifacts =
        RoundChangeArtifacts.create(
            Collections.singletonList(messageFactory.createRoundChange(roundIdentifier, empty())));

    round.startRoundWith(roundChangeArtifacts, 15);
    verify(transmitter, times(1))
        .multicastProposal(
            eq(roundIdentifier),
            blockCaptor.capture(),
            eq(Optional.of(roundChangeArtifacts.getRoundChangeCertificate())));

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory.createPrepare(roundIdentifier, proposedBlock.getHash()));
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void creatingNewBlockNotifiesBlockMiningObservers() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);
    round.createAndSendProposalMessage(15);
    verify(minedBlockObserver).blockMined(any());
  }

  @Test
  public void blockIsOnlyImportedOnceWhenCommitsAreReceivedBeforeProposal() {
    final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
    final int QUORUM_SIZE = 2;
    final RoundState roundState = new RoundState(roundIdentifier, QUORUM_SIZE, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createProposal(roundIdentifier, proposedBlock, Optional.empty()));

    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void blockIsImportedOnlyOnceIfQuorumCommitsAreReceivedPriorToProposal() {
    final int QUORUM_SIZE = 1;
    final RoundState roundState = new RoundState(roundIdentifier, QUORUM_SIZE, messageValidator);
    final IbftRound round =
        new IbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            localNodeKeys,
            messageFactory,
            transmitter,
            roundTimer);

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createProposal(roundIdentifier, proposedBlock, Optional.empty()));

    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }
}
