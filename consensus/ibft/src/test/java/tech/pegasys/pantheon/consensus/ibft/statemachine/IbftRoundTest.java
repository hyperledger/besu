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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.blockcreation.IbftBlockCreator;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMessageTransmitter;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MinedBlockObserver;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.util.Subscribers;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
  private final Subscribers<MinedBlockObserver> subscribers = new Subscribers<>();
  private ProtocolContext<IbftContext> protocolContext;

  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private BlockImporter<IbftContext> blockImporter;
  @Mock private IbftMessageTransmitter transmitter;
  @Mock private MinedBlockObserver minedBlockObserver;
  @Mock private IbftBlockCreator blockCreator;
  @Mock private MessageValidator messageValidator;

  @Captor private ArgumentCaptor<SignedData<ProposalPayload>> payloadArgCaptor;

  private Block proposedBlock;
  private IbftExtraData proposedExtraData;

  private final Signature remoteCommitSeal =
      Signature.create(BigInteger.ONE, BigInteger.ONE, (byte) 1);

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext<>(
            blockChain,
            worldStateArchive,
            new IbftContext(new VoteTally(Collections.emptyList()), new VoteProposer()));

    when(messageValidator.addSignedProposalPayload(any())).thenReturn(true);
    when(messageValidator.validatePrepareMessage(any())).thenReturn(true);
    when(messageValidator.validateCommmitMessage(any())).thenReturn(true);

    proposedExtraData =
        new IbftExtraData(
            BytesValue.wrap(new byte[32]),
            Collections.emptyList(),
            Optional.empty(),
            0,
            Collections.emptyList());
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    headerTestFixture.extraData(proposedExtraData.encode());
    headerTestFixture.number(1);

    final BlockHeader header = headerTestFixture.buildHeader();
    proposedBlock =
        new Block(header, new BlockBody(Collections.emptyList(), Collections.emptyList()));

    when(blockCreator.createBlock(anyLong())).thenReturn(proposedBlock);

    when(blockImporter.importBlock(any(), any(), any())).thenReturn(true);

    subscribers.subscribe(minedBlockObserver);
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
            transmitter);

    round.handleProposalMessage(
        messageFactory.createSignedProposalPayload(roundIdentifier, proposedBlock));
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
            transmitter);

    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1)).multicastProposal(roundIdentifier, proposedBlock);
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
            transmitter);
    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1)).multicastProposal(roundIdentifier, proposedBlock);
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
            transmitter);

    final Hash commitSealHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(
            proposedBlock.getHeader(), proposedExtraData);
    final Signature localCommitSeal = SECP256K1.sign(commitSealHash, localNodeKeys);

    // Receive Proposal Message
    round.handleProposalMessage(
        messageFactory.createSignedProposalPayload(roundIdentifier, proposedBlock));
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, times(1))
        .multicastCommit(roundIdentifier, proposedBlock.getHash(), localCommitSeal);
    verify(blockImporter, never()).importBlock(any(), any(), any());

    // Receive Commit Message

    round.handleCommitMessage(
        messageFactory.createSignedCommitPayload(
            roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    // Should import block when both commit seals are available.
    ArgumentCaptor<Block> capturedBlock = ArgumentCaptor.forClass(Block.class);
    verify(blockImporter, times(1)).importBlock(any(), capturedBlock.capture(), any());

    // Ensure imported block contains both commit seals.
    IbftExtraData importedExtraData =
        IbftExtraData.decode(capturedBlock.getValue().getHeader().getExtraData());
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
            transmitter);

    final Hash commitSealHash =
        IbftBlockHashing.calculateDataHashForCommittedSeal(
            proposedBlock.getHeader(), proposedExtraData);
    final Signature localCommitSeal = SECP256K1.sign(commitSealHash, localNodeKeys);

    round.createAndSendProposalMessage(15);
    verify(transmitter, never()).multicastCommit(any(), any(), any());
    verify(blockImporter, never()).importBlock(any(), any(), any());

    round.handlePrepareMessage(
        messageFactory.createSignedPreparePayload(roundIdentifier, proposedBlock.getHash()));

    verify(transmitter, times(1))
        .multicastCommit(roundIdentifier, proposedBlock.getHash(), localCommitSeal);
    verify(blockImporter, never()).importBlock(any(), any(), any());

    round.handleCommitMessage(
        messageFactory.createSignedCommitPayload(
            roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));
    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void aNewRoundMessageWithAnewBlockIsSentUponReceptionOfARoundChangeWithNoCertificate() {
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
            transmitter);

    final RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(Collections.emptyList());

    round.startRoundWith(roundChangeCertificate, 15);
    verify(transmitter, times(1))
        .multicastNewRound(eq(roundIdentifier), eq(roundChangeCertificate), any());
  }

  @Test
  public void aNewRoundMessageWithTheSameBlockIsSentUponReceptionOfARoundChangeWithCertificate() {
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
            transmitter);

    final RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(
            Collections.singletonList(
                messageFactory
                    .createSignedRoundChangePayload(
                        roundIdentifier,
                        Optional.of(
                            new PreparedCertificate(
                                messageFactory
                                    .createSignedProposalPayload(priorRoundChange, proposedBlock)
                                    .getSignedPayload(),
                                Collections.emptyList())))
                    .getSignedPayload()));
    // NOTE: IbftRound assumes the prepare's are valid

    round.startRoundWith(roundChangeCertificate, 15);
    verify(transmitter, times(1))
        .multicastNewRound(
            eq(roundIdentifier), eq(roundChangeCertificate), payloadArgCaptor.capture());

    final IbftExtraData proposedExtraData =
        IbftExtraData.decode(
            payloadArgCaptor.getValue().getPayload().getBlock().getHeader().getExtraData());
    assertThat(proposedExtraData.getRound()).isEqualTo(roundIdentifier.getRoundNumber());

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory.createSignedPreparePayload(roundIdentifier, proposedBlock.getHash()));
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
            transmitter);

    final RoundChangeCertificate roundChangeCertificate =
        new RoundChangeCertificate(
            Collections.singletonList(
                messageFactory
                    .createSignedRoundChangePayload(roundIdentifier, Optional.empty())
                    .getSignedPayload()));

    round.startRoundWith(roundChangeCertificate, 15);
    verify(transmitter, times(1))
        .multicastNewRound(
            eq(roundIdentifier), eq(roundChangeCertificate), payloadArgCaptor.capture());

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory.createSignedPreparePayload(roundIdentifier, proposedBlock.getHash()));
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
            transmitter);
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
            transmitter);

    round.handleCommitMessage(
        messageFactory.createSignedCommitPayload(
            roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createSignedProposalPayload(roundIdentifier, proposedBlock));

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
            transmitter);

    round.handleCommitMessage(
        messageFactory.createSignedCommitPayload(
            roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createSignedProposalPayload(roundIdentifier, proposedBlock));

    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }
}
