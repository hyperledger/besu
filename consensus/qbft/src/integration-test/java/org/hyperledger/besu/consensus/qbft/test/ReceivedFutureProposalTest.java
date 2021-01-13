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
package org.hyperledger.besu.consensus.qbft.test;

import static org.hyperledger.besu.consensus.qbft.support.IntegrationTestHelpers.createValidPreparedCertificate;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.statemachine.PreparedCertificate;
import org.hyperledger.besu.consensus.qbft.support.IntegrationTestHelpers;
import org.hyperledger.besu.consensus.qbft.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.consensus.qbft.support.ValidatorPeer;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collections;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Ensure the Qbft component responds appropriately when a future round Proposal message is
 * received.
 */
public class ReceivedFutureProposalTest {

  private final int NETWORK_SIZE = 5;

  // Configuration ensures remote peer will provide proposal for first block
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .buildAndStart();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  @Test
  public void proposalWithEmptyPrepareCertificatesOfferNewBlock() {
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);
    final Block blockToPropose =
        context.createBlockForProposalFromChainHead(nextRoundId.getRoundNumber(), 15);
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(targetRound);

    final ValidatorPeer nextProposer = context.roundSpecificPeers(nextRoundId).getProposer();

    nextProposer.injectProposalForFutureRound(
        targetRound, roundChanges, Collections.emptyList(), blockToPropose);

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(targetRound, blockToPropose.getHash());

    peers.verifyMessagesReceived(expectedPrepare);
  }

  @Ignore("Requires validation")
  @Test
  public void proposalFromIllegalSenderIsDiscardedAndNoPrepareForNewRoundIsSent() {
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);
    final Block blockToPropose =
        context.createBlockForProposalFromChainHead(nextRoundId.getRoundNumber(), 15);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId);

    final ValidatorPeer illegalProposer =
        context.roundSpecificPeers(nextRoundId).getNonProposing(0);

    illegalProposer.injectProposalForFutureRound(
        nextRoundId, roundChanges, Collections.emptyList(), blockToPropose);

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void proposalWithPrepareCertificateResultsInNewRoundStartingWithExpectedBlock() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedRoundArtifacts =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId, preparedRoundArtifacts);

    final ValidatorPeer nextProposer = context.roundSpecificPeers(nextRoundId).getProposer();

    nextProposer.injectProposalForFutureRound(
        nextRoundId, roundChanges, Collections.emptyList(), reproposedBlock);

    peers.verifyMessagesReceived(
        localNodeMessageFactory.createPrepare(nextRoundId, reproposedBlock.getHash()));
  }

  @Test
  public void proposalMessageForPriorRoundIsNotActioned() {
    // first move to a future round, then inject a proposal for a prior round, local node
    // should send no messages.
    final ConsensusRoundIdentifier futureRound = new ConsensusRoundIdentifier(1, 2);
    peers.roundChange(futureRound);

    final ConsensusRoundIdentifier interimRound = new ConsensusRoundIdentifier(1, 1);
    final List<SignedData<RoundChangePayload>> roundChangePayloads =
        peers.createSignedRoundChangePayload(interimRound);

    final ValidatorPeer interimRoundProposer =
        context.roundSpecificPeers(interimRound).getProposer();

    interimRoundProposer.injectProposalForFutureRound(
        interimRound,
        roundChangePayloads,
        Collections.emptyList(),
        context.createBlockForProposalFromChainHead(1, 30));

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void receiveRoundStateIsNotLostIfASecondProposalMessageIsReceivedForCurrentRound() {
    final Block initialBlock = context.createBlockForProposalFromChainHead(0, 15);
    final Block reproposedBlock = context.createBlockForProposalFromChainHead(1, 15);
    final ConsensusRoundIdentifier nextRoundId = new ConsensusRoundIdentifier(1, 1);

    final PreparedCertificate preparedRoundArtifacts =
        createValidPreparedCertificate(context, roundId, initialBlock);

    final List<SignedData<RoundChangePayload>> roundChanges =
        peers.createSignedRoundChangePayload(nextRoundId, preparedRoundArtifacts);

    final RoundSpecificPeers nextRoles = context.roundSpecificPeers(nextRoundId);
    final ValidatorPeer nextProposer = nextRoles.getProposer();

    nextProposer.injectProposalForFutureRound(
        nextRoundId, roundChanges, preparedRoundArtifacts.getPrepares(), reproposedBlock);

    peers.verifyMessagesReceived(
        localNodeMessageFactory.createPrepare(nextRoundId, reproposedBlock.getHash()));

    // Inject a prepare, then re-inject the proposal - then ensure only a single prepare is enough
    // to trigger a Commit transmission from the local node
    nextRoles.getNonProposing(0).injectPrepare(nextRoundId, reproposedBlock.getHash());

    nextProposer.injectProposalForFutureRound(
        nextRoundId, roundChanges, preparedRoundArtifacts.getPrepares(), reproposedBlock);
    peers.verifyNoMessagesReceived();

    nextRoles.getNonProposing(1).injectPrepare(nextRoundId, reproposedBlock.getHash());

    final Commit expectedCommit =
        new Commit(
            IntegrationTestHelpers.createSignedCommitPayload(
                nextRoundId, reproposedBlock, context.getLocalNodeParams().getNodeKey()));

    peers.verifyMessagesReceived(expectedCommit);
  }
}
