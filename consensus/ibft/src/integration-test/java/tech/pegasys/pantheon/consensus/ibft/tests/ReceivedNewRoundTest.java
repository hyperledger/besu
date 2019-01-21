/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.tests;

import static java.util.Optional.empty;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createValidPreparedCertificate;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

/** Ensure the Ibft component responds appropriately when a NewRound message is received. */
public class ReceivedNewRoundTest {

  private final int NETWORK_SIZE = 5;

  // Configuration ensures remote peer will provide proposal for first block
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  @Before
  public void setup() {
    context.getController().start();
  }

  @Test
  public void newRoundMessageWithEmptyPrepareCertificatesOfferNewBlock() {
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);
    final Block blockToPropose =
        context.createBlockForProposalFromChainHead(nextRoundId.getRoundNumber(), 15);
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);

    final List<SignedData<RoundChangePayload>> roundChanges =
        roles
            .getAllPeers()
            .stream()
            .map(p -> p.getMessageFactory().createSignedRoundChangePayload(targetRound, empty()))
            .collect(Collectors.toList());

    final ValidatorPeer nextProposer = context.getRoundSpecificRoles(nextRoundId).getProposer();

    nextProposer.injectNewRound(
        targetRound,
        new RoundChangeCertificate(roundChanges),
        nextProposer.getMessageFactory().createSignedProposalPayload(targetRound, blockToPropose));

    final SignedData<PreparePayload> expectedPrepare =
        localNodeMessageFactory.createSignedPreparePayload(targetRound, blockToPropose.getHash());

    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepare);
  }

  @Test
  public void newRoundMessageFromIllegalSenderIsDiscardedAndNoPrepareForNewRoundIsSent() {
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);
    final Block blockToPropose =
        context.createBlockForProposalFromChainHead(nextRoundId.getRoundNumber(), 15);

    final List<SignedData<RoundChangePayload>> roundChanges =
        roles
            .getAllPeers()
            .stream()
            .map(p -> p.getMessageFactory().createSignedRoundChangePayload(nextRoundId, empty()))
            .collect(Collectors.toList());

    final ValidatorPeer illegalProposer =
        context.getRoundSpecificRoles(nextRoundId).getNonProposingPeer(0);

    illegalProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        illegalProposer
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, blockToPropose));

    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void newRoundWithPrepareCertificateResultsInNewRoundStartingWithExpectedBlock() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedCertificate =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        roles
            .getAllPeers()
            .stream()
            .map(
                p ->
                    p.getMessageFactory()
                        .createSignedRoundChangePayload(
                            nextRoundId, Optional.of(preparedCertificate)))
            .collect(Collectors.toList());

    final ValidatorPeer nextProposer = context.getRoundSpecificRoles(nextRoundId).getProposer();

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        roles
            .getNonProposingPeer(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock));

    assertPeersReceivedExactly(
        roles.getAllPeers(),
        localNodeMessageFactory.createSignedPreparePayload(nextRoundId, reproposedBlock.getHash()));
  }

  @Test
  public void newRoundMessageForPriorRoundIsNotActioned() {
    // first move to a future round, then inject a newRound for a prior round, local node
    // should send no messages.
    final ConsensusRoundIdentifier futureRound = new ConsensusRoundIdentifier(1, 2);
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      peer.injectRoundChange(futureRound, empty());
    }

    final ConsensusRoundIdentifier interimRound = new ConsensusRoundIdentifier(1, 1);
    final List<SignedData<RoundChangePayload>> roundChangePayloads = Lists.newArrayList();
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      roundChangePayloads.add(
          peer.getMessageFactory().createSignedRoundChangePayload(interimRound, empty()));
    }

    final ValidatorPeer interimRoundProposer =
        context.getRoundSpecificRoles(interimRound).getProposer();

    final SignedData<ProposalPayload> proposal =
        interimRoundProposer
            .getMessageFactory()
            .createSignedProposalPayload(
                interimRound, context.createBlockForProposalFromChainHead(1, 30));

    interimRoundProposer.injectNewRound(
        interimRound, new RoundChangeCertificate(roundChangePayloads), proposal);

    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void receiveRoundStateIsNotLostIfASecondNewRoundMessageIsReceivedForCurrentRound() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedCertificate =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        roles
            .getAllPeers()
            .stream()
            .map(
                p ->
                    p.getMessageFactory()
                        .createSignedRoundChangePayload(
                            nextRoundId, Optional.of(preparedCertificate)))
            .collect(Collectors.toList());

    final RoundSpecificNodeRoles nextRoles = context.getRoundSpecificRoles(nextRoundId);
    final ValidatorPeer nextProposer = nextRoles.getProposer();

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        roles
            .getNonProposingPeer(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock));

    assertPeersReceivedExactly(
        roles.getAllPeers(),
        localNodeMessageFactory.createSignedPreparePayload(nextRoundId, reproposedBlock.getHash()));

    // Inject a prepare, then re-inject the newRound - then ensure only a single prepare is enough
    // to trigger a Commit transmission from the local node
    nextRoles.getNonProposingPeer(0).injectPrepare(nextRoundId, reproposedBlock.getHash());

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        roles
            .getNonProposingPeer(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock));

    assertPeersReceivedNoMessages(roles.getAllPeers());

    nextRoles.getNonProposingPeer(1).injectPrepare(nextRoundId, reproposedBlock.getHash());

    final SignedData<CommitPayload> expectedCommit =
        TestHelpers.createSignedCommentPayload(
            nextRoundId, reproposedBlock, context.getLocalNodeParams().getNodeKeyPair());

    assertPeersReceivedExactly(nextRoles.getAllPeers(), expectedCommit);
  }
}
