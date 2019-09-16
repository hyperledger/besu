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
package org.hyperledger.besu.consensus.ibft.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.ibft.support.IntegrationTestHelpers.createSignedCommitPayload;

import org.hyperledger.besu.consensus.ibft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.ibft.ibftevent.BlockTimerExpiry;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.ibft.support.TestContext;
import org.hyperledger.besu.consensus.ibft.support.TestContextBuilder;
import org.hyperledger.besu.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

/**
 * These tests assume the basic function of the Ibft Round State Machine has been proven via the
 * LocalNodeIsNotProposerTest.
 */
public class LocalNodeIsProposerTest {

  private final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  private final int NETWORK_SIZE = 4;

  // Configuration ensures unit under test will be responsible for sending first proposal
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(1)
          .clock(fixedClock)
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private Block expectedProposedBlock;
  private Proposal expectedTxProposal;
  private Commit expectedTxCommit;

  @Before
  public void setup() {
    expectedProposedBlock = context.createBlockForProposalFromChainHead(0, blockTimeStamp);
    expectedTxProposal =
        localNodeMessageFactory.createProposal(roundId, expectedProposedBlock, Optional.empty());

    expectedTxCommit =
        new Commit(
            createSignedCommitPayload(
                roundId, expectedProposedBlock, context.getLocalNodeParams().getNodeKeyPair()));

    // Trigger "block timer" to send proposal.
    context.getController().handleBlockTimerExpiry(new BlockTimerExpiry(roundId));
  }

  @Test
  public void basicCase() {
    peers.verifyMessagesReceived(expectedTxProposal);

    // NOTE: In these test roles.getProposer() will return NULL.
    peers.getNonProposing(0).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(1).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyMessagesReceived(expectedTxCommit);

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(2).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();
  }

  @Test
  public void importsToChainWithoutReceivingPrepareMessages() {
    peers.verifyMessagesReceived(expectedTxProposal);

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(2).injectCommit(roundId, expectedProposedBlock.getHash());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();
  }
}
