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
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.core.support.TestContext;
import org.hyperledger.besu.consensus.qbft.core.support.TestContextBuilder;
import org.hyperledger.besu.ethereum.core.Block;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LocalNodeNotProposerTest {

  private final int NETWORK_SIZE = 4;
  // By setting the indexOfFirstLocallyProposedBlock to 0 (and that the blockchain has only the
  // genesis block) guarantees the local node is not responsible for proposing the first block).

  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .buildAndStart();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private final Block blockToPropose =
      context.createBlockForProposalFromChainHead(15, peers.getProposer().getNodeAddress());

  private Prepare expectedTxPrepare;
  private Commit expectedTxCommit;

  @BeforeEach
  public void setup() {
    expectedTxPrepare = localNodeMessageFactory.createPrepare(roundId, blockToPropose.getHash());

    expectedTxCommit =
        new Commit(
            createSignedCommitPayload(
                roundId, blockToPropose, context.getLocalNodeParams().getNodeKey()));
  }

  @Test
  public void preparesReceivedFromNonProposerIsValid() {
    peers.getProposer().injectProposal(roundId, blockToPropose);

    peers.verifyMessagesReceived(expectedTxPrepare);

    peers.getNonProposing(0).injectPrepare(roundId, blockToPropose.getHash());
    peers.verifyNoMessagesReceived();

    peers.getNonProposing(1).injectPrepare(roundId, blockToPropose.getHash());
    peers.verifyMessagesReceived(expectedTxCommit);

    // Ensure the local blockchain has NOT incremented yet.
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // NO further messages should be transmitted when another Prepare is received.
    peers.getNonProposing(1).injectPrepare(roundId, blockToPropose.getHash());
    peers.verifyNoMessagesReceived();

    // Inject a commit, ensure blockChain is not updated, and no message are sent (not quorum yet)
    peers.getNonProposing(0).injectCommit(roundId, blockToPropose);
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // A second commit message means quorum is reached, and blockchain should be updated.
    peers.getNonProposing(1).injectCommit(roundId, blockToPropose);
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);

    // ensure any further commit messages do not affect the system
    peers.getProposer().injectCommit(roundId, blockToPropose);
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void commitMessagesReceivedBeforePrepareCorrectlyImports() {
    // All peers send a commit, then all non-proposing peers send a prepare, when then Proposal
    // arrives last, the chain is updated, and a prepare and commit message are transmitted.
    peers.clearReceivedMessages();
    peers.commit(roundId, blockToPropose);
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    peers.prepareForNonProposing(roundId, blockToPropose.getHash());
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    peers.getProposer().injectProposal(roundId, blockToPropose);
    // TODO(tmm): Unfortunately, there are times that the Commit will go out BEFORE the prepare
    // This is one of them :( Maybe fix the testing to be ignorant of ordering?
    peers.verifyMessagesReceived(expectedTxCommit, expectedTxPrepare);
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void
      canImportABlockIfSufficientCommitsReceivedWithoutPreparesAndThatNoPacketsSentAfterImport() {
    peers.commit(roundId, blockToPropose);
    peers.verifyNoMessagesReceived();
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    peers.getProposer().injectProposal(roundId, blockToPropose);
    peers.verifyMessagesReceived(expectedTxPrepare);
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);

    peers.getNonProposing(0).injectPrepare(roundId, blockToPropose.getHash());
    peers.verifyNoMessagesReceived();
  }
}
