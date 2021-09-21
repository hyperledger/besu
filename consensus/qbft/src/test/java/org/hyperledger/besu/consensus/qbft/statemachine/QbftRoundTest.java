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
import static java.util.Optional.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.RoundTimer;
import org.hyperledger.besu.consensus.common.bft.blockcreation.BftBlockCreator;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.network.QbftMessageTransmitter;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.validation.MessageValidator;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MinedBlockObserver;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;
import org.hyperledger.besu.util.Subscribers;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class QbftRoundTest {

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final NodeKey nodeKey2 = NodeKeyUtils.generate();
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private final MessageFactory messageFactory = new MessageFactory(nodeKey);
  private final MessageFactory messageFactory2 = new MessageFactory(nodeKey2);
  private final Subscribers<MinedBlockObserver> subscribers = Subscribers.create();
  private final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();
  private ProtocolContext protocolContext;

  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private BlockImporter blockImporter;
  @Mock private QbftMessageTransmitter transmitter;
  @Mock private MinedBlockObserver minedBlockObserver;
  @Mock private BftBlockCreator blockCreator;
  @Mock private MessageValidator messageValidator;
  @Mock private RoundTimer roundTimer;

  @Captor private ArgumentCaptor<Block> blockCaptor;

  private Block proposedBlock;
  private BftExtraData proposedExtraData;

  private final SECPSignature remoteCommitSeal =
      SignatureAlgorithmFactory.getInstance()
          .createSignature(BigInteger.ONE, BigInteger.ONE, (byte) 1);

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            setupContextWithBftExtraDataEncoder(
                QbftContext.class, emptyList(), new QbftExtraDataCodec()));

    when(messageValidator.validateProposal(any())).thenReturn(true);
    when(messageValidator.validatePrepare(any())).thenReturn(true);
    when(messageValidator.validateCommit(any())).thenReturn(true);

    proposedExtraData =
        new BftExtraData(Bytes.wrap(new byte[32]), emptyList(), empty(), 0, emptyList());
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    headerTestFixture.extraData(new QbftExtraDataCodec().encode(proposedExtraData));
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
    new QbftRound(
        roundState,
        blockCreator,
        protocolContext,
        blockImporter,
        subscribers,
        nodeKey,
        messageFactory,
        transmitter,
        roundTimer,
        bftExtraDataCodec);
    verify(roundTimer, times(1)).startTimer(roundIdentifier);
  }

  @Test
  public void onReceptionOfValidProposalSendsAPrepareToNetworkPeers() {
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.handleProposalMessage(
        messageFactory.createProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList()));
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, never()).multicastCommit(any(), any(), any());
  }

  @Test
  public void sendsAProposalAndPrepareWhenSendProposalRequested() {
    final RoundState roundState = new RoundState(roundIdentifier, 3, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1))
        .multicastProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList());
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, never()).multicastCommit(any(), any(), any());
  }

  @Test
  public void singleValidatorImportBlocksImmediatelyOnProposalCreation() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);
    round.createAndSendProposalMessage(15);
    verify(transmitter, times(1))
        .multicastProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList());
    verify(transmitter, times(1)).multicastPrepare(roundIdentifier, proposedBlock.getHash());
    verify(transmitter, times(1)).multicastCommit(any(), any(), any());
    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void localNodeProposesToNetworkOfTwoValidatorsImportsOnReceptionOfCommitFromPeer() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    final Hash commitSealHash =
        new BftBlockHashing(new QbftExtraDataCodec())
            .calculateDataHashForCommittedSeal(proposedBlock.getHeader(), proposedExtraData);
    final SECPSignature localCommitSeal = nodeKey.sign(commitSealHash);

    round.createAndSendProposalMessage(15);
    verify(transmitter, never()).multicastCommit(any(), any(), any());
    verify(blockImporter, never()).importBlock(any(), any(), any());

    round.handlePrepareMessage(
        messageFactory2.createPrepare(roundIdentifier, proposedBlock.getHash()));

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
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.startRoundWith(new RoundChangeArtifacts(emptyList(), Optional.empty()), 15);
    verify(transmitter, times(1))
        .multicastProposal(eq(roundIdentifier), any(), eq(emptyList()), eq(emptyList()));
    verify(transmitter, times(1)).multicastPrepare(eq(roundIdentifier), any());
  }

  @Test
  public void aProposalMessageWithTheSameBlockIsSentUponReceptionOfARoundChangeWithCertificate() {
    final ConsensusRoundIdentifier priorRoundChange = new ConsensusRoundIdentifier(1, 0);
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    final SignedData<PreparePayload> preparedPayload =
        messageFactory.createPrepare(priorRoundChange, proposedBlock.getHash()).getSignedPayload();

    final RoundChange roundChange =
        messageFactory.createRoundChange(
            roundIdentifier,
            Optional.of(new PreparedCertificate(proposedBlock, singletonList(preparedPayload), 2)));

    final RoundChangeArtifacts roundChangeArtifacts =
        RoundChangeArtifacts.create(singletonList(roundChange));

    round.startRoundWith(roundChangeArtifacts, 15);
    verify(transmitter, times(1))
        .multicastProposal(
            eq(roundIdentifier),
            blockCaptor.capture(),
            eq(singletonList(roundChange.getSignedPayload())),
            eq(singletonList(preparedPayload)));
    verify(transmitter, times(1))
        .multicastPrepare(eq(roundIdentifier), eq(blockCaptor.getValue().getHash()));

    final BftExtraData proposedExtraData =
        new QbftExtraDataCodec().decode(blockCaptor.getValue().getHeader());
    assertThat(proposedExtraData.getRound()).isEqualTo(roundIdentifier.getRoundNumber());

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory2.createPrepare(roundIdentifier, proposedBlock.getHash()));
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void creatingNewBlockFromEmptyPreparedCertificateUpdatesInternalState() {
    final RoundState roundState = new RoundState(roundIdentifier, 2, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    final RoundChange roundChange =
        messageFactory.createRoundChange(roundIdentifier, Optional.empty());

    final RoundChangeArtifacts roundChangeArtifacts =
        RoundChangeArtifacts.create(List.of(roundChange));

    round.startRoundWith(roundChangeArtifacts, 15);
    verify(transmitter, times(1))
        .multicastProposal(
            eq(roundIdentifier),
            blockCaptor.capture(),
            eq(List.of(roundChange.getSignedPayload())),
            eq(Collections.emptyList()));
    verify(transmitter, times(1))
        .multicastPrepare(eq(roundIdentifier), eq(blockCaptor.getValue().getHash()));

    // Inject a single Prepare message, and confirm the roundState has gone to Prepared (which
    // indicates the block has entered the roundState (note: all msgs are deemed valid due to mocks)
    round.handlePrepareMessage(
        messageFactory2.createPrepare(roundIdentifier, proposedBlock.getHash()));
    assertThat(roundState.isPrepared()).isTrue();
  }

  @Test
  public void creatingNewBlockNotifiesBlockMiningObservers() {
    final RoundState roundState = new RoundState(roundIdentifier, 1, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);
    round.createAndSendProposalMessage(15);
    verify(minedBlockObserver).blockMined(any());
  }

  @Test
  public void blockIsOnlyImportedOnceWhenCommitsAreReceivedBeforeProposal() {
    final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
    final int QUORUM_SIZE = 2;
    final RoundState roundState = new RoundState(roundIdentifier, QUORUM_SIZE, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList()));

    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void blockIsImportedOnlyOnceIfQuorumCommitsAreReceivedPriorToProposal() {
    final int QUORUM_SIZE = 1;
    final RoundState roundState = new RoundState(roundIdentifier, QUORUM_SIZE, messageValidator);
    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            nodeKey,
            messageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.handleCommitMessage(
        messageFactory.createCommit(roundIdentifier, proposedBlock.getHash(), remoteCommitSeal));

    round.handleProposalMessage(
        messageFactory.createProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList()));

    verify(blockImporter, times(1)).importBlock(any(), any(), any());
  }

  @Test
  public void exceptionDuringNodeKeySigningDoesNotEscape() {
    final int QUORUM_SIZE = 1;
    final RoundState roundState = new RoundState(roundIdentifier, QUORUM_SIZE, messageValidator);
    final NodeKey throwingNodeKey = mock(NodeKey.class);
    final MessageFactory throwingMessageFactory = new MessageFactory(throwingNodeKey);
    when(throwingNodeKey.sign(any())).thenThrow(new SecurityModuleException("Hsm is Offline"));

    final QbftRound round =
        new QbftRound(
            roundState,
            blockCreator,
            protocolContext,
            blockImporter,
            subscribers,
            throwingNodeKey,
            throwingMessageFactory,
            transmitter,
            roundTimer,
            bftExtraDataCodec);

    round.handleProposalMessage(
        messageFactory.createProposal(
            roundIdentifier, proposedBlock, Collections.emptyList(), Collections.emptyList()));

    // Verify that no prepare message was constructed by the QbftRound
    assertThat(roundState.constructPreparedCertificate()).isEmpty();

    verifyNoInteractions(transmitter);
  }
}
