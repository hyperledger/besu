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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.emptyList;
import static org.hyperledger.besu.consensus.qbft.support.IntegrationTestHelpers.createSignedCommitPayload;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

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
          .buildAndStart();

  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final ConsensusRoundIdentifier futureHeightRoundId = new ConsensusRoundIdentifier(2, 0);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();
  private final BftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

  @Test
  public void messagesForFutureHeightAreBufferedUntilChainHeightCatchesUp() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(30);
    final Block signedCurrentHeightBlock =
        BftHelpers.createSealedBlock(
            bftExtraDataCodec, currentHeightBlock, 0, peers.sign(currentHeightBlock.getHash()));

    final Block futureHeightBlock =
        context.createBlockForProposal(
            signedCurrentHeightBlock.getHeader(), 60, peers.getProposer().getNodeAddress());

    peers.getProposer().injectProposal(futureHeightRoundId, futureHeightBlock);
    peers.verifyNoMessagesReceived();

    // verify that we have incremented the estimated height of the proposer.
    peers
        .getProposer()
        .verifyEstimatedChainHeightEquals(futureHeightBlock.getHeader().getNumber() - 1);

    // Inject prepares and commits from all peers
    peers.prepareForNonProposing(futureHeightRoundId, futureHeightBlock.getHash());
    peers.forNonProposing(
        peer ->
            peer.verifyEstimatedChainHeightEquals(futureHeightBlock.getHeader().getNumber() - 1));

    peers.commitForNonProposing(futureHeightRoundId, futureHeightBlock);

    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // Add block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    final Prepare expectedPrepareMessage =
        localNodeMessageFactory.createPrepare(futureHeightRoundId, futureHeightBlock.getHash());

    final Commit expectedCommitMessage =
        new Commit(
            createSignedCommitPayload(
                futureHeightRoundId, futureHeightBlock, context.getLocalNodeParams().getNodeKey()));

    peers.verifyMessagesReceived(expectedPrepareMessage, expectedCommitMessage);
    assertThat(context.getCurrentChainHeight()).isEqualTo(2);
  }

  @Test
  public void messagesFromPreviousHeightAreDiscarded() {
    final Block currentHeightBlock =
        context.createBlockForProposalFromChainHead(30, peers.getProposer().getNodeAddress());
    final Block signedCurrentHeightBlock =
        BftHelpers.createSealedBlock(
            bftExtraDataCodec, currentHeightBlock, 0, peers.sign(currentHeightBlock.getHash()));

    peers.getProposer().injectProposal(roundId, currentHeightBlock);
    peers.getNonProposing(0).injectPrepare(roundId, currentHeightBlock.getHash());

    final Prepare expectedPrepareMessage =
        localNodeMessageFactory.createPrepare(roundId, currentHeightBlock.getHash());

    peers.verifyMessagesReceived(expectedPrepareMessage);

    // Add block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    // Inject prepares and commits from all peers for the 'previous' round (i.e. the height
    // from before the block arrived).
    peers.prepareForNonProposing(roundId, currentHeightBlock.getHash());
    peers.commitForNonProposing(roundId, currentHeightBlock);

    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void multipleNewChainHeadEventsDoesNotRestartCurrentHeightManager() {
    final Block currentHeightBlock =
        context.createBlockForProposalFromChainHead(30, peers.getProposer().getNodeAddress());

    peers.getProposer().injectProposal(roundId, currentHeightBlock);
    peers.getProposer().injectPrepare(roundId, currentHeightBlock.getHash());
    peers.getNonProposing(0).injectPrepare(roundId, currentHeightBlock.getHash());

    peers.clearReceivedMessages();

    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(context.getBlockchain().getChainHeadHeader()));

    // Should only require 1 more prepare to close it out
    peers.getNonProposing(1).injectPrepare(roundId, currentHeightBlock.getHash());

    final Commit expectedCommitMessage =
        new Commit(
            createSignedCommitPayload(
                roundId, currentHeightBlock, context.getLocalNodeParams().getNodeKey()));
    peers.verifyMessagesReceived(expectedCommitMessage);
  }

  @Test
  public void correctMessagesAreExtractedFromFutureHeightBuffer() {
    final Block currentHeightBlock = context.createBlockForProposalFromChainHead(30);
    final Block signedCurrentHeightBlock =
        BftHelpers.createSealedBlock(
            bftExtraDataCodec, currentHeightBlock, 0, peers.sign(currentHeightBlock.getHash()));

    final Block nextHeightBlock =
        context.createBlockForProposal(
            signedCurrentHeightBlock.getHeader(), 60, peers.getProposer().getNodeAddress());
    final Block signedNextHeightBlock =
        BftHelpers.createSealedBlock(
            bftExtraDataCodec, nextHeightBlock, 0, peers.sign(nextHeightBlock.getHash()));

    final Block futureHeightBlock =
        context.createBlockForProposal(
            signedNextHeightBlock.getHeader(), 90, peers.getNonProposing(0).getNodeAddress());

    final ConsensusRoundIdentifier nextHeightRoundId = new ConsensusRoundIdentifier(2, 0);
    final ConsensusRoundIdentifier futureHeightRoundId = new ConsensusRoundIdentifier(3, 0);

    // Inject prepares and commits from all peers into FutureHeight (2 height time)
    peers.prepareForNonProposing(futureHeightRoundId, futureHeightBlock.getHash());
    peers.commitForNonProposing(futureHeightRoundId, futureHeightBlock);

    // Add the "interim" block to chain, and notify system of its arrival.
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    peers.verifyNoMessagesReceived();
    peers.getProposer().injectProposal(nextHeightRoundId, nextHeightBlock);

    final Prepare expectedPrepareMessage =
        localNodeMessageFactory.createPrepare(nextHeightRoundId, nextHeightBlock.getHash());

    // Assert ONLY a prepare message was received, not any commits (i.e. futureHeightRoundId
    // messages have not been used.
    peers.verifyMessagesReceived(expectedPrepareMessage);

    // Future height proposal needs to come from the NEXT nonProposer.
    peers.getNonProposing(0).injectProposal(futureHeightRoundId, futureHeightBlock);

    // Change to the FutureRound, and confirm prepare and commit msgs are sent
    context.getBlockchain().appendBlock(signedNextHeightBlock, emptyList());
    assertThat(context.getCurrentChainHeight()).isEqualTo(2);
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedNextHeightBlock.getHeader()));

    final Prepare expectedFuturePrepareMessage =
        localNodeMessageFactory.createPrepare(futureHeightRoundId, futureHeightBlock.getHash());

    final Commit expectedCommitMessage =
        new Commit(
            createSignedCommitPayload(
                futureHeightRoundId, futureHeightBlock, context.getLocalNodeParams().getNodeKey()));

    // Assert ONLY a prepare message was received, not any commits (i.e. futureHeightRoundId
    // messages have not been used.
    peers.verifyMessagesReceived(expectedCommitMessage, expectedFuturePrepareMessage);
  }
}
