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
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createSignedCommitPayload;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.BlockTimerExpiry;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Before;
import org.junit.Test;

/**
 * These tests assume the basic function of the Ibft Round State Machine has been proven via the
 * LocalNodeIsNotProposerTest.
 */
public class LocalNodeIsProposerTest {

  final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  final int NETWORK_SIZE = 4;

  // Configuration ensures unit under test will be responsible for sending first proposal
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(1)
          .clock(fixedClock)
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private Block expectedProposedBlock;
  private SignedData<ProposalPayload> expectedTxProposal;
  private SignedData<CommitPayload> expectedTxCommit;

  @Before
  public void setup() {
    expectedProposedBlock = context.createBlockForProposalFromChainHead(0, blockTimeStamp);
    expectedTxProposal =
        localNodeMessageFactory.createSignedProposalPayload(roundId, expectedProposedBlock);

    expectedTxCommit =
        createSignedCommitPayload(
            roundId, expectedProposedBlock, context.getLocalNodeParams().getNodeKeyPair());

    // Start the Controller, and trigger "block timer" to send proposal.
    context.getController().start();
    context.getController().handleBlockTimerExpiry(new BlockTimerExpiry(roundId));
  }

  @Test
  public void basicCase() {
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxProposal);

    // NOTE: In these test roles.getProposer() will return NULL.
    roles.getNonProposingPeer(0).injectPrepare(roundId, expectedProposedBlock.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());

    roles.getNonProposingPeer(1).injectPrepare(roundId, expectedProposedBlock.getHash());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxCommit);

    roles.getNonProposingPeer(1).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    assertPeersReceivedNoMessages(roles.getAllPeers());

    roles.getNonProposingPeer(2).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void importsToChainWithoutReceivingPrepareMessages() {
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxProposal);

    roles.getNonProposingPeer(1).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    assertPeersReceivedNoMessages(roles.getAllPeers());

    roles.getNonProposingPeer(2).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }
}
