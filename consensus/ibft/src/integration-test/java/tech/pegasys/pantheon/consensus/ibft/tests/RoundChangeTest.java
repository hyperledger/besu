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

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createValidPreparedCertificate;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.RoundExpiry;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

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
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private final Block blockToPropose = context.createBlockForProposalFromChainHead(0, 15);

  @Before
  public void setup() {
    context.getController().start();
  }

  @Test
  public void onRoundChangeTimerExpiryEventRoundChangeMessageIsSent() {

    // NOTE: The prepare certificate will be empty as insufficient Prepare msgs have been received.
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final SignedData<RoundChangePayload> expectedTxRoundChange =
        localNodeMessageFactory.createSignedRoundChangePayload(targetRound, empty());
    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasEmptyCertificateIfNoPrepareMessagesReceived() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final SignedData<RoundChangePayload> expectedTxRoundChange =
        localNodeMessageFactory.createSignedRoundChangePayload(targetRound, empty());

    roles.getProposer().injectProposal(roundId, blockToPropose);
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasEmptyCertificateIfInsufficientPreparesAreReceived() {
    // Note: There are 4 validators, thus Quorum is 3 and Prepare Msgs are 2 - thus
    // receiving only a single Prepare msg will result in no PreparedCert.
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final SignedData<RoundChangePayload> expectedTxRoundChange =
        localNodeMessageFactory.createSignedRoundChangePayload(targetRound, empty());

    roles.getProposer().injectProposal(roundId, blockToPropose);
    roles.getNonProposingPeer(1).injectPrepare(roundId, blockToPropose.getHash());
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxRoundChange);
  }

  @Test
  public void roundChangeHasPopulatedCertificateIfQuorumPrepareMessagesAndProposalAreReceived() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 1);
    final SignedData<PreparePayload> localPrepareMessage =
        localNodeMessageFactory.createSignedPreparePayload(roundId, blockToPropose.getHash());

    final SignedData<ProposalPayload> proposal =
        roles.getProposer().injectProposal(roundId, blockToPropose);
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);

    final SignedData<PreparePayload> p1 =
        roles.getNonProposingPeer(0).injectPrepare(roundId, blockToPropose.getHash());
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);
    final SignedData<PreparePayload> p2 =
        roles.getNonProposingPeer(1).injectPrepare(roundId, blockToPropose.getHash());
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);

    final SignedData<RoundChangePayload> expectedTxRoundChange =
        localNodeMessageFactory.createSignedRoundChangePayload(
            targetRound,
            Optional.of(
                new PreparedCertificate(
                    proposal, Lists.newArrayList(localPrepareMessage, p1, p2))));

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxRoundChange);
  }

  @Test
  public void whenSufficientRoundChangeMessagesAreReceivedForNewRoundLocalNodeCreatesNewRoundMsg() {
    // Note: Round-4 is the next round for which the local node is Proposer
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);
    final Block locallyProposedBlock =
        context.createBlockForProposalFromChainHead(targetRound.getRoundNumber(), blockTimeStamp);

    final SignedData<RoundChangePayload> rc1 =
        roles.getNonProposingPeer(0).injectRoundChange(targetRound, empty());
    final SignedData<RoundChangePayload> rc2 =
        roles.getNonProposingPeer(1).injectRoundChange(targetRound, empty());
    final SignedData<RoundChangePayload> rc3 =
        roles.getNonProposingPeer(2).injectRoundChange(targetRound, empty());
    final SignedData<RoundChangePayload> rc4 =
        roles.getProposer().injectRoundChange(targetRound, empty());

    final SignedData<NewRoundPayload> expectedNewRound =
        localNodeMessageFactory.createSignedNewRoundPayload(
            targetRound,
            new RoundChangeCertificate(Lists.newArrayList(rc1, rc2, rc3, rc4)),
            localNodeMessageFactory.createSignedProposalPayload(targetRound, locallyProposedBlock));

    assertPeersReceivedExactly(roles.getAllPeers(), expectedNewRound);
  }

  @Test
  public void newRoundMessageContainsBlockOnWhichPeerPrepared() {
    final long ARBITRARY_BLOCKTIME = 1500;

    final PreparedCertificate earlierPrepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 1),
            context.createBlockForProposalFromChainHead(1, ARBITRARY_BLOCKTIME / 2));

    final PreparedCertificate bestPrepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 2),
            context.createBlockForProposalFromChainHead(2, ARBITRARY_BLOCKTIME));

    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final SignedData<RoundChangePayload> rc1 =
        roles.getNonProposingPeer(0).injectRoundChange(targetRound, empty());

    // Create a roundChange with a PreparedCertificate from an earlier Round (should not be used
    final SignedData<RoundChangePayload> rc2 =
        roles.getNonProposingPeer(1).injectRoundChange(targetRound, Optional.of(earlierPrepCert));

    // Create a roundChange with a PreparedCertificate from an earlier Round (should not be used
    final SignedData<RoundChangePayload> rc3 =
        roles.getNonProposingPeer(2).injectRoundChange(targetRound, Optional.of(earlierPrepCert));

    // Create a roundChange containing a PreparedCertificate
    final SignedData<RoundChangePayload> rc4 =
        roles.getProposer().injectRoundChange(targetRound, Optional.of(bestPrepCert));

    // Expected to use the block with "ARBITRARY_BLOCKTIME" (i.e. latter block) but with the target
    // round number.
    final Block expectedBlockToPropose =
        context.createBlockForProposalFromChainHead(
            targetRound.getRoundNumber(), ARBITRARY_BLOCKTIME);

    final SignedData<NewRoundPayload> expectedNewRound =
        localNodeMessageFactory.createSignedNewRoundPayload(
            targetRound,
            new RoundChangeCertificate(Lists.newArrayList(rc1, rc2, rc3, rc4)),
            localNodeMessageFactory.createSignedProposalPayload(
                targetRound, expectedBlockToPropose));

    assertPeersReceivedExactly(roles.getAllPeers(), expectedNewRound);
  }

  @Test
  public void cannotRoundChangeToAnEarlierRound() {
    // Controller always starts at 1:0. This test moves to 1:7, then attempts to move back to 1:3.

    final ConsensusRoundIdentifier futureRound = new ConsensusRoundIdentifier(1, 9);
    final List<SignedData<RoundChangePayload>> roundChangeMessages = Lists.newArrayList();
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      roundChangeMessages.add(peer.injectRoundChange(futureRound, empty()));
    }

    final ConsensusRoundIdentifier priorRound = new ConsensusRoundIdentifier(1, 4);
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      peer.injectRoundChange(priorRound, empty());
    }

    final Block locallyProposedBlock =
        context.createBlockForProposalFromChainHead(futureRound.getRoundNumber(), blockTimeStamp);

    final SignedData<NewRoundPayload> expectedNewRound =
        localNodeMessageFactory.createSignedNewRoundPayload(
            futureRound,
            new RoundChangeCertificate(roundChangeMessages),
            localNodeMessageFactory.createSignedProposalPayload(futureRound, locallyProposedBlock));

    assertPeersReceivedExactly(roles.getAllPeers(), expectedNewRound);
  }

  @Test
  public void multipleRoundChangeMessagesFromSamePeerDoesNotTriggerRoundChange() {
    // Note: Round-3 is the next round for which the local node is Proposer
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final ValidatorPeer transmitter = roles.getNonProposingPeer(0);

    for (int i = 0; i < IbftHelpers.calculateRequiredValidatorQuorum(NETWORK_SIZE); i++) {
      transmitter.injectRoundChange(targetRound, empty());
    }

    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void subsequentRoundChangeMessagesFromPeerDoNotOverwritePriorMessage() {
    final long ARBITRARY_BLOCKTIME = 1500;

    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final PreparedCertificate prepCert =
        createValidPreparedCertificate(
            context,
            new ConsensusRoundIdentifier(1, 2),
            context.createBlockForProposalFromChainHead(2, ARBITRARY_BLOCKTIME));

    List<SignedData<RoundChangePayload>> roundChangeMessages = Lists.newArrayList();
    // Create a roundChange containing a PreparedCertificate
    roundChangeMessages.add(
        roles.getProposer().injectRoundChange(targetRound, Optional.of(prepCert)));

    // Attempt to override the previously received RoundChange (but now without a payload).
    roles.getProposer().injectRoundChange(targetRound, empty());

    roundChangeMessages.addAll(
        roles
            .getNonProposingPeers()
            .stream()
            .map(peer -> peer.injectRoundChange(targetRound, empty()))
            .collect(Collectors.toList()));

    final Block expectedBlockToPropose =
        context.createBlockForProposalFromChainHead(
            targetRound.getRoundNumber(), ARBITRARY_BLOCKTIME);

    final SignedData<NewRoundPayload> expectedNewRound =
        localNodeMessageFactory.createSignedNewRoundPayload(
            targetRound,
            new RoundChangeCertificate(Lists.newArrayList(roundChangeMessages)),
            localNodeMessageFactory.createSignedProposalPayload(
                targetRound, expectedBlockToPropose));

    assertPeersReceivedExactly(roles.getAllPeers(), expectedNewRound);
  }

  @Test
  public void messagesFromPreviousRoundAreDiscardedOnTransitionToFutureRound() {
    roles.getProposer().injectProposal(roundId, blockToPropose);

    // timeout into next round
    context.getController().handleRoundExpiry(new RoundExpiry(roundId));

    // Clear prior Prepare msg and RoundChange message
    roles.getAllPeers().forEach(ValidatorPeer::clearReceivedMessages);

    // inject enough prepares from prior round to trigger a commit
    roles
        .getNonProposingPeers()
        .forEach(peer -> peer.injectPrepare(roundId, blockToPropose.getHash()));

    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void roundChangeExpiryForNonCurrentRoundIsDiscarded() {
    // Manually timeout a future round, and ensure no messages are sent
    context.getController().handleRoundExpiry(new RoundExpiry(new ConsensusRoundIdentifier(1, 1)));
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void illegallyConstructedRoundChangeMessageIsDiscarded() {
    final ConsensusRoundIdentifier targetRound = new ConsensusRoundIdentifier(1, 4);

    final SignedData<RoundChangePayload> rc1 =
        roles.getNonProposingPeer(0).injectRoundChange(targetRound, empty());
    final SignedData<RoundChangePayload> rc2 =
        roles.getNonProposingPeer(1).injectRoundChange(targetRound, empty());
    final SignedData<RoundChangePayload> rc3 =
        roles.getNonProposingPeer(2).injectRoundChange(targetRound, empty());

    // create illegal RoundChangeMessage
    final PreparedCertificate illegalPreparedCertificate =
        new PreparedCertificate(
            roles
                .getNonProposingPeer(0)
                .getMessageFactory()
                .createSignedProposalPayload(roundId, blockToPropose),
            emptyList());

    roles
        .getNonProposingPeer(2)
        .injectRoundChange(targetRound, Optional.of(illegalPreparedCertificate));

    // Ensure no NewRound message is sent.
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }
}
