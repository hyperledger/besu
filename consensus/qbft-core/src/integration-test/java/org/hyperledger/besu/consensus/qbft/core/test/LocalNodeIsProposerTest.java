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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.qbft.core.support.IntegrationTestHelpers.createSignedCommitPayload;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.events.BlockTimerExpiry;
import org.hyperledger.besu.consensus.common.bft.events.NewChainHead;
import org.hyperledger.besu.consensus.common.bft.events.RoundExpiry;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.core.support.TestContext;
import org.hyperledger.besu.consensus.qbft.core.support.TestContextBuilder;
import org.hyperledger.besu.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * These tests assume the basic function of the Qbft Round State Machine has been proven via the
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
          .buildAndStart();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private Block expectedProposedBlock;
  private Proposal expectedTxProposal;
  private Commit expectedTxCommit;
  private Prepare expectedTxPrepare;

  @BeforeEach
  public void setup() {
    expectedProposedBlock = context.createBlockForProposalFromChainHead(blockTimeStamp);
    expectedTxProposal =
        localNodeMessageFactory.createProposal(
            roundId, expectedProposedBlock, Collections.emptyList(), Collections.emptyList());

    expectedTxPrepare =
        localNodeMessageFactory.createPrepare(roundId, expectedProposedBlock.getHash());

    expectedTxCommit =
        new Commit(
            createSignedCommitPayload(
                roundId, expectedProposedBlock, context.getLocalNodeParams().getNodeKey()));

    // Trigger "block timer" to send proposal.
    context.getController().handleBlockTimerExpiry(new BlockTimerExpiry(roundId));
  }

  @Test
  public void basicCase() {
    peers.verifyMessagesReceived(expectedTxProposal, expectedTxPrepare);

    // NOTE: In these test roles.getProposer() will return NULL.
    peers.getNonProposing(0).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(1).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyMessagesReceived(expectedTxCommit);

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(2).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();
  }

  @Test
  public void importsToChainWithoutReceivingPrepareMessages() {
    peers.verifyMessagesReceived(expectedTxProposal, expectedTxPrepare);

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(2).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();
  }

  @Test
  public void nodeDoesNotSendRoundChangeIfRoundTimesOutAfterBlockImportButBeforeNewBlock() {
    peers.verifyMessagesReceived(expectedTxProposal, expectedTxPrepare);
    peers.getNonProposing(0).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();

    context.getController().handleRoundExpiry(new RoundExpiry(roundId));
    peers.verifyNoMessagesReceived();

    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(expectedProposedBlock.getHeader()));
    peers.verifyNoMessagesReceived();
  }

  @Test
  public void nodeDoesNotSendCommitMessageAfterBlockIsImportedAndBeforeNewBlockEvent() {
    peers.verifyMessagesReceived(expectedTxProposal, expectedTxPrepare);
    peers.getNonProposing(0).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(1).injectCommit(roundId, expectedProposedBlock);
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(0).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyNoMessagesReceived();
    peers.getNonProposing(1).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyNoMessagesReceived();
    peers.getNonProposing(2).injectPrepare(roundId, expectedProposedBlock.getHash());
    peers.verifyNoMessagesReceived();
  }
}
