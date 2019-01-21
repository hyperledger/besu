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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.emptyList;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createSignedCommitPayload;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

public class FutureHeightTest {

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

  private final ConsensusRoundIdentifier futureHeightRoundId = new ConsensusRoundIdentifier(2, 0);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  @Before
  public void setup() {
    context.getController().start();
  }

  @Test
  public void messagesForFutureHeightAreBufferedUntilChainHeightCatchesUp() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(0, 30);
    final Block signedCurrentHeightBlock =
        IbftHelpers.createSealedBlock(
            currentHeightBlock,
            roles
                .getAllPeers()
                .stream()
                .map(peer -> peer.getBlockSignature(currentHeightBlock.getHash()))
                .collect(Collectors.toList()));

    final Block futureHeightBlock =
        context.createBlockForProposal(signedCurrentHeightBlock.getHeader(), 0, 60);

    roles.getProposer().injectProposal(futureHeightRoundId, futureHeightBlock);
    assertPeersReceivedNoMessages(roles.getAllPeers());

    // Inject prepares and commits from all peers
    roles
        .getNonProposingPeers()
        .forEach(
            p -> {
              p.injectPrepare(futureHeightRoundId, futureHeightBlock.getHash());
              p.injectCommit(futureHeightRoundId, futureHeightBlock.getHash());
            });
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // Add block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    final SignedData<PreparePayload> expectedPrepareMessage =
        localNodeMessageFactory.createSignedPreparePayload(
            futureHeightRoundId, futureHeightBlock.getHash());

    final SignedData<CommitPayload> expectedCommitMessage =
        createSignedCommitPayload(
            futureHeightRoundId, futureHeightBlock, context.getLocalNodeParams().getNodeKeyPair());

    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepareMessage, expectedCommitMessage);
    assertThat(context.getCurrentChainHeight()).isEqualTo(2);
  }

  @Test
  public void messagesFromPreviousHeightAreDiscarded() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(0, 30);
    final Block signedCurrentHeightBlock =
        IbftHelpers.createSealedBlock(
            currentHeightBlock,
            roles
                .getAllPeers()
                .stream()
                .map(peer -> peer.getBlockSignature(currentHeightBlock.getHash()))
                .collect(Collectors.toList()));

    roles.getProposer().injectProposal(roundId, currentHeightBlock);
    roles.getNonProposingPeer(0).injectPrepare(roundId, currentHeightBlock.getHash());

    final SignedData<PreparePayload> expectedPrepareMessage =
        localNodeMessageFactory.createSignedPreparePayload(roundId, currentHeightBlock.getHash());

    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepareMessage);

    // Add block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    // Inject prepares and commits from all peers for the 'previous' round (i.e. the height
    // from before the block arrived).
    roles
        .getNonProposingPeers()
        .forEach(
            p -> {
              p.injectPrepare(roundId, currentHeightBlock.getHash());
              p.injectCommit(roundId, currentHeightBlock.getHash());
            });
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void multipleNewChainHeadEventsDoesNotRestartCurrentHeightManager() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(0, 30);

    roles.getProposer().injectProposal(roundId, currentHeightBlock);
    roles.getNonProposingPeer(0).injectPrepare(roundId, currentHeightBlock.getHash());

    roles.getAllPeers().forEach(peer -> peer.clearReceivedMessages());

    // inject a NewHeight FOR THE CURRENT HEIGHT
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(context.getBlockchain().getChainHeadHeader()));

    // Should only require 1 more prepare to close it out
    roles.getNonProposingPeer(1).injectPrepare(roundId, currentHeightBlock.getHash());

    final SignedData<CommitPayload> expectedCommitMessage =
        createSignedCommitPayload(
            roundId, currentHeightBlock, context.getLocalNodeParams().getNodeKeyPair());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedCommitMessage);
  }

  @Test
  public void correctMessagesAreExtractedFromFutureHeightBuffer() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(0, 30);
    final Block signedCurrentHeightBlock =
        IbftHelpers.createSealedBlock(
            currentHeightBlock,
            roles
                .getAllPeers()
                .stream()
                .map(peer -> peer.getBlockSignature(currentHeightBlock.getHash()))
                .collect(Collectors.toList()));

    final Block nextHeightBlock =
        context.createBlockForProposal(signedCurrentHeightBlock.getHeader(), 0, 60);
    final Block signedNextHeightBlock =
        IbftHelpers.createSealedBlock(
            nextHeightBlock,
            roles
                .getAllPeers()
                .stream()
                .map(peer -> peer.getBlockSignature(nextHeightBlock.getHash()))
                .collect(Collectors.toList()));

    final Block futureHeightBlock =
        context.createBlockForProposal(signedNextHeightBlock.getHeader(), 0, 90);

    final ConsensusRoundIdentifier nextHeightRoundId = new ConsensusRoundIdentifier(2, 0);
    final ConsensusRoundIdentifier futureHeightRoundId = new ConsensusRoundIdentifier(3, 0);

    // Inject prepares and commits from all peers into FutureHeight (2 height time)
    roles
        .getNonProposingPeers()
        .forEach(
            p -> {
              p.injectPrepare(futureHeightRoundId, futureHeightBlock.getHash());
              p.injectCommit(futureHeightRoundId, futureHeightBlock.getHash());
            });

    // Add the "interim" block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    assertPeersReceivedNoMessages(roles.getAllPeers());
    roles.getProposer().injectProposal(nextHeightRoundId, nextHeightBlock);

    final SignedData<PreparePayload> expectedPrepareMessage =
        localNodeMessageFactory.createSignedPreparePayload(
            nextHeightRoundId, nextHeightBlock.getHash());

    // Assert ONLY a prepare message was received, not any commits (i.e. futureHeightRoundId
    // messages have not been used.
    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepareMessage);

    roles.getProposer().injectProposal(futureHeightRoundId, futureHeightBlock);

    // Change to the FutureRound, and confirm prepare and commit msgs are sent
    context.getBlockchain().appendBlock(signedNextHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(2);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedNextHeightBlock.getHeader()));

    final SignedData<PreparePayload> expectedFuturePrepareMessage =
        localNodeMessageFactory.createSignedPreparePayload(
            futureHeightRoundId, futureHeightBlock.getHash());

    final SignedData<CommitPayload> expectedCommitMessage =
        createSignedCommitPayload(
            futureHeightRoundId, futureHeightBlock, context.getLocalNodeParams().getNodeKeyPair());

    // Assert ONLY a prepare message was received, not any commits (i.e. futureHeightRoundId
    // messages have not been used.
    assertPeersReceivedExactly(
        roles.getAllPeers(), expectedCommitMessage, expectedFuturePrepareMessage);
  }
}
