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
package org.hyperledger.besu.consensus.qbft.core.test;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.hyperledger.besu.consensus.qbft.core.support.IntegrationTestHelpers.createValidPreparedCertificate;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.core.statemachine.PreparedCertificate;
import org.hyperledger.besu.consensus.qbft.core.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.core.support.TestContext;
import org.hyperledger.besu.consensus.qbft.core.support.TestContextBuilder;
import org.hyperledger.besu.consensus.qbft.core.support.ValidatorPeer;
import org.hyperledger.besu.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.Test;

public class RoundChangeTest {

  private final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  private final int NETWORK_SIZE = 5;

  // Configuration ensures remote peer will provide proposal for first block
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .clock(fixedClock)
          .buildAndStart();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private final Block blockToPropose =
      context.createBlockForProposalFromChainHead(15, peers.getProposer().getNodeAddress());

  @Test
  public void onRoundChangeTimerExpiryEventRoundChangeMessageIsSent() {

    // NOTE: The prepare certificate will be empty as insufficient Prepare msgs have been received.
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final RoundChange expectedTxRoundChange =
        localNodeMessageFactory.createRoundChange(targetRound, empty());
    context.getController().handleBlockTimerExpiry(new BlockTimerExpiry(roundId));
    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    peers.verifyMessagesReceived(expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasEmptyCertificateIfNoPrepareMessagesReceived() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final RoundChange expectedTxRoundChange =
        localNodeMessageFactory.createRoundChange(targetRound, empty());

    peers.getProposer().injectProposal(roundId, blockToPropose);
    peers.clearReceivedMessages();

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    peers.verifyMessagesReceived(expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasEmptyCertificateIfInsufficientPreparesAreReceived() {
    // Note: There are 4 validators, thus Quorum is 3 and Prepare Msgs are 2 - thus
    // receiving only a single Prepare msg will result in no PreparedCert.
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final RoundChange expectedTxRoundChange =
        localNodeMessageFactory.createRoundChange(targetRound, empty());

    peers.getProposer().injectProposal(roundId, blockToPropose);
    peers.getNonProposing(1).injectPrepare(roundId, blockToPropose.getHash());
    peers.clearReceivedMessages();

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    peers.verifyMessagesReceived(expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasPopulatedCertificateIfQuorumPrepareMessagesAndProposalAreReceived() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final Prepare localPrepareMessage =
        localNodeMessageFactory.createPrepare(roundId, blockToPropose.getHash());

    peers.getProposer().injectProposal(roundId, blockToPropose);
    final Prepare p0 = peers.getProposer().injectPrepare(roundId, blockToPropose.getHash());
    peers.clearReceivedMessages();

    final Prepare p1 = peers.getNonProposing(0).injectPrepare(roundId, blockToPropose.getHash());
    peers.clearReceivedMessages();

    final Prepare p2 = peers.getNonProposing(1).injectPrepare(roundId, blockToPropose.getHash());
    peers.clearReceivedMessages();

    final RoundChange expectedTxRoundChange =
        localNodeMessageFactory.createRoundChange(
            targetRound,
            Optional.of(
                new PreparedCertificate(
                    blockToPropose,
                    List.of(localPrepareMessage, p0, p1, p2).stream()
                        .map(Prepare::getSignedPayload)
                        .collect(Collectors.toList()),
                    roundId.getRoundNumber())));

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    peers.verifyMessagesReceived(expectedTxRoundChange);
  }

  @Test
  public void whenSufficientRoundChangeMessagesAreReceivedForNewRoundLocalNodeCreatesProposalMsg() {
    // Note: Round-4 is the next round for which the local node is Proposer
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);
    final Block locallyProposedBlock =
        context.createBlockForProposalFromChainHead(blockTimeStamp, 4);

    final RoundChange rc1 = peers.getNonProposing(0).injectRoundChange(targetRound, empty());
    final RoundChange rc2 = peers.getNonProposing(1).injectRoundChange(targetRound, empty());
    final RoundChange rc3 = peers.getNonProposing(2).injectRoundChange(targetRound, empty());
    final RoundChange rc4 = peers.getProposer().injectRoundChange(targetRound, empty());

    final Proposal expectedProposal =
        localNodeMessageFactory.createProposal(
            targetRound,
            locallyProposedBlock,
            Lists.newArrayList(
                rc1.getSignedPayload(),
                rc2.getSignedPayload(),
                rc3.getSignedPayload(),
                rc4.getSignedPayload()),
            emptyList());

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(targetRound, locallyProposedBlock.getHash());

    peers.verifyMessagesReceived(expectedProposal, expectedPrepare);
  }

  @Test
  public void proposalMessageContainsBlockOnWhichPeerPrepared() {
    final long ARBITRARY_BLOCKTIME = 1500;

    final PreparedCertificate earlierPrepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 1),
            context.createBlockForProposalFromChainHead(
                ARBITRARY_BLOCKTIME / 2, peers.getProposer().getNodeAddress(), 1));

    final PreparedCertificate bestPrepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 2),
            context.createBlockForProposalFromChainHead(
                ARBITRARY_BLOCKTIME, peers.getProposer().getNodeAddress(), 2));

    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final RoundChange rc1 = peers.getNonProposing(0).injectRoundChange(targetRound, empty());

    // Create a roundChange with a PreparedCertificate from an earlier Round (should not be used
    final RoundChange rc2 =
        peers.getNonProposing(1).injectRoundChange(targetRound, Optional.of(earlierPrepCert));

    // Create a roundChange with a PreparedCertificate from an earlier Round (should not be used
    final RoundChange rc3 =
        peers.getNonProposing(2).injectRoundChange(targetRound, Optional.of(earlierPrepCert));

    // Create a roundChange containing a PreparedCertificate
    final RoundChange rc4 =
        peers.getProposer().injectRoundChange(targetRound, Optional.of(bestPrepCert));

    // Expected to use the block with "ARBITRARY_BLOCKTIME" (i.e. latter block) but with the target
    // round number.
    final Block expectedBlockToPropose =
        context.createBlockForProposalFromChainHead(
            ARBITRARY_BLOCKTIME, peers.getProposer().getNodeAddress(), 4);

    final Proposal expectedProposal =
        localNodeMessageFactory.createProposal(
            targetRound,
            expectedBlockToPropose,
            Lists.newArrayList(
                rc1.getSignedPayload(),
                rc2.getSignedPayload(),
                rc3.getSignedPayload(),
                rc4.getSignedPayload()),
            bestPrepCert.getPrepares());

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(targetRound, expectedBlockToPropose.getHash());

    peers.verifyMessagesReceived(expectedProposal, expectedPrepare);
  }

  @Test
  public void cannotRoundChangeToAnEarlierRound() {
    // Controller always starts at 1:0. This test moves to 1:9, then attempts to move back to 1:4.
    final ConsensusRoundIdentifier futureRound = new ConsensusRoundIdentifier(1, 9);
    final List<SignedData<RoundChangePayload>> roundChangeMessages = peers.roundChange(futureRound);

    final ConsensusRoundIdentifier priorRound = new ConsensusRoundIdentifier(1, 4);
    peers.roundChange(priorRound);

    final Block locallyProposedBlock =
        context.createBlockForProposalFromChainHead(blockTimeStamp, 9);

    final Proposal expectedProposal =
        localNodeMessageFactory.createProposal(
            futureRound, locallyProposedBlock, roundChangeMessages, emptyList());

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(futureRound, locallyProposedBlock.getHash());

    peers.verifyMessagesReceived(expectedProposal, expectedPrepare);
  }

  @Test
  public void multipleRoundChangeMessagesFromSamePeerDoesNotTriggerRoundChange() {
    // Note: Round-3 is the next round for which the local node is Proposer
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final ValidatorPeer transmitter = peers.getNonProposing(0);

    for (int i = 0; i < BftHelpers.calculateRequiredValidatorQuorum(NETWORK_SIZE); i++) {
      transmitter.injectRoundChange(targetRound, empty());
    }

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void subsequentRoundChangeMessagesFromPeerDoNotOverwritePriorMessage() {
    final long ARBITRARY_BLOCKTIME = 1500;

    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final PreparedCertificate prepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 2),
            context.createBlockForProposalFromChainHead(
                ARBITRARY_BLOCKTIME, peers.getProposer().getNodeAddress(), 2));

    final List<SignedData<RoundChangePayload>> roundChangeMessages = Lists.newArrayList();
    // Create a roundChange containing a PreparedCertificate
    roundChangeMessages.add(
        peers
            .getProposer()
            .injectRoundChange(targetRound, Optional.of(prepCert))
            .getSignedPayload());

    // Attempt to override the previously received RoundChange (but now without a payload).
    peers.getProposer().injectRoundChange(targetRound, empty());

    roundChangeMessages.addAll(peers.roundChangeForNonProposing(targetRound));

    final Block expectedBlockToPropose =
        context.createBlockForProposalFromChainHead(
            ARBITRARY_BLOCKTIME, peers.getProposer().getNodeAddress(), 4);

    final Proposal expectedProposal =
        localNodeMessageFactory.createProposal(
            targetRound,
            expectedBlockToPropose,
            Lists.newArrayList(roundChangeMessages),
            prepCert.getPrepares());

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(targetRound, expectedBlockToPropose.getHash());

    peers.verifyMessagesReceived(expectedProposal, expectedPrepare);
  }

  @Test
  public void messagesFromPreviousRoundAreDiscardedOnTransitionToFutureRound() {
    peers.getProposer().injectProposal(roundId, blockToPropose);

    // timeout into next round
    context.getController().handleRoundExpiry(new RoundExpiry(roundId));

    // Clear prior Prepare msg and RoundChange message
    peers.clearReceivedMessages();

    // inject enough prepares from prior round to trigger a commit
    peers.prepareForNonProposing(roundId, blockToPropose.getHash());

    peers.verifyNoMessagesReceived();
  }

  @Test
  public void roundChangeExpiryForNonCurrentRoundIsDiscarded() {
    // Manually timeout a future round, and ensure no messages are sent
    context.getController().handleRoundExpiry(new RoundExpiry(new ConsensusRoundIdentifier(1, 1)));
    peers.verifyNoMessagesReceived();
  }

  @Test
  public void illegallyConstructedRoundChangeMessageIsDiscarded() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    peers.getNonProposing(0).injectRoundChange(targetRound, empty());
    peers.getNonProposing(1).injectRoundChange(targetRound, empty());
    peers.getNonProposing(2).injectRoundChange(targetRound, empty());

    // create illegal RoundChangeMessage
    peers
        .getNonProposing(0)
        .getMessageFactory()
        .createProposal(roundId, blockToPropose, emptyList(), emptyList());
    final PreparedCertificate illegalPreparedRoundArtifacts =
        new PreparedCertificate(blockToPropose, emptyList(), 1);

    peers.getProposer().injectRoundChange(targetRound, Optional.of(illegalPreparedRoundArtifacts));

    // Ensure no Proposal message is sent.
    peers.verifyNoMessagesReceived();
  }
}
