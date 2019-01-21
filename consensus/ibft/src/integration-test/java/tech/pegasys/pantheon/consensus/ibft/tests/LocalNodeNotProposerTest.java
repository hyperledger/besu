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
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createSignedCommentPayload;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.ethereum.core.Block;

import org.junit.Before;
import org.junit.Test;

public class LocalNodeNotProposerTest {

  final int NETWORK_SIZE = 4;
  // By setting the indexOfFirstLocallyProposedBlock to 0 (and that the blockchain has only the
  // genesis block) guarantees the local node is not responsible for proposing the first block).

  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  private final Block blockToPropose = context.createBlockForProposalFromChainHead(0, 15);

  private SignedData<PreparePayload> expectedTxPrepare;
  private SignedData<CommitPayload> expectedTxCommit;

  @Before
  public void setup() {
    expectedTxPrepare =
        localNodeMessageFactory.createSignedPreparePayload(roundId, blockToPropose.getHash());

    expectedTxCommit =
        createSignedCommentPayload(
            roundId, blockToPropose, context.getLocalNodeParams().getNodeKeyPair());

    context.getController().start();
  }

  @Test
  public void basicCase() {
    roles.getProposer().injectProposal(roundId, blockToPropose);

    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxPrepare);

    roles.getNonProposingPeer(0).injectPrepare(roundId, blockToPropose.getHash());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxCommit);

    // Ensure the local blockchain has NOT incremented yet.
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // NO further messages should be transmitted when another Prepare is received.
    roles.getNonProposingPeer(1).injectPrepare(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());

    // Inject a commit, ensure blockChain is not updated, and no message are sent (not quorum yet)
    roles.getNonProposingPeer(0).injectCommit(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // A second commit message means quorum is reached, and blockchain should be updated.
    roles.getNonProposingPeer(1).injectCommit(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);

    // ensure any further commit messages do not affect the system
    roles.getProposer().injectCommit(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void prepareFromProposerIsIgnored() {
    roles.getProposer().injectProposal(roundId, blockToPropose);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxPrepare);

    // No commit message transmitted after receiving prepare from proposer
    roles.getProposer().injectPrepare(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    roles.getNonProposingPeer(1).injectPrepare(roundId, blockToPropose.getHash());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxCommit);

    // Inject a commit, ensure blockChain is not updated, and no message are sent (not quorum yet)
    roles.getNonProposingPeer(0).injectCommit(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // A second commit message means quorum is reached, and blockchain should be updated.
    roles.getNonProposingPeer(1).injectCommit(roundId, blockToPropose.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void commitMessagesReceivedBeforePrepareCorrectlyImports() {
    // All peers send a commit, then all non-proposing peers send a prepare, when then Proposal
    // arrives last, the chain is updated, and a prepare and commit message are transmitted.
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      peer.injectCommit(roundId, blockToPropose.getHash());
      assertPeersReceivedNoMessages(roles.getAllPeers());
      assertThat(context.getCurrentChainHeight()).isEqualTo(0);
    }

    for (final ValidatorPeer peer : roles.getNonProposingPeers()) {
      peer.injectPrepare(roundId, blockToPropose.getHash());
      assertPeersReceivedNoMessages(roles.getAllPeers());
      assertThat(context.getCurrentChainHeight()).isEqualTo(0);
    }

    roles.getProposer().injectProposal(roundId, blockToPropose);
    // TODO(tmm): Unfortunatley, there are times that the Commit will go out BEFORE the prepare
    // This is one of them :( Maybe fix the testing to be ignorant of ordering?
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxCommit, expectedTxPrepare);
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void
      fullQuorumOfCommitMessagesReceivedThenProposalImportsBlockCommitSentAfterFinalPrepare() {
    for (final ValidatorPeer peer : roles.getAllPeers()) {
      peer.injectCommit(roundId, blockToPropose.getHash());
      assertPeersReceivedNoMessages(roles.getAllPeers());
      assertThat(context.getCurrentChainHeight()).isEqualTo(0);
    }

    roles.getProposer().injectProposal(roundId, blockToPropose);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxPrepare);
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);

    roles.getNonProposingPeer(0).injectPrepare(roundId, blockToPropose.getHash());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedTxCommit);
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }
}
