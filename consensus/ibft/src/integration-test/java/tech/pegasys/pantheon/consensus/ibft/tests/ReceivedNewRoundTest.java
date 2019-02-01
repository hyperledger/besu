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

import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createValidPreparedCertificate;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificPeers;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.List;

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
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

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
        peers.createSignedRoundChangePayload(targetRound);

    final ValidatorPeer nextProposer = context.roundSpecificPeers(nextRoundId).getProposer();

    nextProposer.injectNewRound(
        targetRound,
        new RoundChangeCertificate(roundChanges),
        nextProposer
            .getMessageFactory()
            .createSignedProposalPayload(targetRound, blockToPropose)
            .getSignedPayload());

    final Prepare expectedPrepare =
        localNodeMessageFactory.createSignedPreparePayload(targetRound, blockToPropose.getHash());

    peers.verifyMessagesReceived(expectedPrepare);
  }

  @Test
  public void newRoundMessageFromIllegalSenderIsDiscardedAndNoPrepareForNewRoundIsSent() {
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);
    final Block blockToPropose =
        context.createBlockForProposalFromChainHead(nextRoundId.getRoundNumber(), 15);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId);

    final ValidatorPeer illegalProposer =
        context.roundSpecificPeers(nextRoundId).getNonProposing(0);

    illegalProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        illegalProposer
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, blockToPropose)
            .getSignedPayload());

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void newRoundWithPrepareCertificateResultsInNewRoundStartingWithExpectedBlock() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedCertificate =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId, preparedCertificate);

    final ValidatorPeer nextProposer = context.roundSpecificPeers(nextRoundId).getProposer();

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        peers
            .getNonProposing(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock)
            .getSignedPayload());

    peers.verifyMessagesReceived(
        localNodeMessageFactory.createSignedPreparePayload(nextRoundId, reproposedBlock.getHash()));
  }

  @Test
  public void newRoundMessageForPriorRoundIsNotActioned() {
    // first move to a future round, then inject a newRound for a prior round, local node
    // should send no messages.
    final ConsensusRoundIdentifier futureRound = new ConsensusRoundIdentifier(1, 2);
    peers.roundChange(futureRound);

    final ConsensusRoundIdentifier interimRound = new ConsensusRoundIdentifier(1, 1);
    final List<SignedData<RoundChangePayload>> roundChangePayloads =
        peers.createSignedRoundChangePayload(interimRound);

    final ValidatorPeer interimRoundProposer =
        context.roundSpecificPeers(interimRound).getProposer();

    final Proposal proposal =
        interimRoundProposer
            .getMessageFactory()
            .createSignedProposalPayload(
                interimRound, context.createBlockForProposalFromChainHead(1, 30));

    interimRoundProposer.injectNewRound(
        interimRound, new RoundChangeCertificate(roundChangePayloads), proposal.getSignedPayload());

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void receiveRoundStateIsNotLostIfASecondNewRoundMessageIsReceivedForCurrentRound() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedCertificate =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId, preparedCertificate);

    final RoundSpecificPeers nextRoles = context.roundSpecificPeers(nextRoundId);
    final ValidatorPeer nextProposer = nextRoles.getProposer();

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        peers
            .getNonProposing(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock)
            .getSignedPayload());

    peers.verifyMessagesReceived(
        localNodeMessageFactory.createSignedPreparePayload(nextRoundId, reproposedBlock.getHash()));

    // Inject a prepare, then re-inject the newRound - then ensure only a single prepare is enough
    // to trigger a Commit transmission from the local node
    nextRoles.getNonProposing(0).injectPrepare(nextRoundId, reproposedBlock.getHash());

    nextProposer.injectNewRound(
        nextRoundId,
        new RoundChangeCertificate(roundChanges),
        peers
            .getNonProposing(0)
            .getMessageFactory()
            .createSignedProposalPayload(nextRoundId, reproposedBlock)
            .getSignedPayload());

    peers.verifyNoMessagesReceived();

    nextRoles.getNonProposing(1).injectPrepare(nextRoundId, reproposedBlock.getHash());

    final Commit expectedCommit =
        new Commit(
            TestHelpers.createSignedCommitPayload(
                nextRoundId, reproposedBlock, context.getLocalNodeParams().getNodeKeyPair()));

    peers.verifyMessagesReceived(expectedCommit);
  }
}
