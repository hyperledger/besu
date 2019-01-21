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
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.injectEmptyNewRound;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Before;
import org.junit.Test;

public class FutureRoundTest {

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

  private final ConsensusRoundIdentifier futureRoundId = new ConsensusRoundIdentifier(1, 5);
  private final RoundSpecificNodeRoles futureRoles = context.getRoundSpecificRoles(futureRoundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  @Before
  public void setup() {
    context.getController().start();
  }

  @Test
  public void messagesForFutureRoundAreNotActionedUntilRoundIsActive() {
    final Block futureBlock =
        context.createBlockForProposalFromChainHead(futureRoundId.getRoundNumber(), 60);
    final int quorum = IbftHelpers.calculateRequiredValidatorQuorum(NETWORK_SIZE);
    final ConsensusRoundIdentifier subsequentRoundId = new ConsensusRoundIdentifier(1, 6);
    final RoundSpecificNodeRoles subsequentRoles = context.getRoundSpecificRoles(subsequentRoundId);

    // required remotely received Prepares = quorum-2
    // required remote received commits = quorum-1

    // Inject 1 too few Commit messages (but sufficient Prepare
    for (int i = 0; i < quorum - 3; i++) {
      futureRoles.getNonProposingPeer(i).injectPrepare(futureRoundId, futureBlock.getHash());
    }

    for (int i = 0; i < quorum - 2; i++) {
      futureRoles.getNonProposingPeer(i).injectCommit(futureRoundId, futureBlock.getHash());
    }

    // inject a prepare and a commit from a subsequent round, and ensure no transmissions are
    // created
    subsequentRoles.getNonProposingPeer(1).injectPrepare(subsequentRoundId, futureBlock.getHash());
    subsequentRoles.getNonProposingPeer(1).injectCommit(subsequentRoundId, futureBlock.getHash());

    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);

    // inject a newRound to move to 'futureRoundId', and ensure localnode sends prepare, commit
    // and updates blockchain
    injectEmptyNewRound(
        futureRoundId, futureRoles.getProposer(), futureRoles.getAllPeers(), futureBlock);

    final SignedData<PreparePayload> expectedPrepare =
        localNodeMessageFactory.createSignedPreparePayload(futureRoundId, futureBlock.getHash());

    assertPeersReceivedExactly(futureRoles.getAllPeers(), expectedPrepare);

    // following 1 more prepare, a commit msg will be sent
    futureRoles.getNonProposingPeer(quorum - 3).injectPrepare(futureRoundId, futureBlock.getHash());

    final SignedData<CommitPayload> expectedCommit =
        localNodeMessageFactory.createSignedCommitPayload(
            futureRoundId,
            futureBlock.getHash(),
            SECP256K1.sign(futureBlock.getHash(), context.getLocalNodeParams().getNodeKeyPair()));
    assertPeersReceivedExactly(futureRoles.getAllPeers(), expectedCommit);

    // requires 1 more commit and the blockchain will progress
    futureRoles.getNonProposingPeer(quorum - 2).injectCommit(futureRoundId, futureBlock.getHash());

    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
  }

  @Test
  public void priorRoundsCannotBeCompletedAfterReceptionOfNewRound() {
    final Block initialBlock =
        context.createBlockForProposalFromChainHead(roundId.getRoundNumber(), 30);
    final Block futureBlock =
        context.createBlockForProposalFromChainHead(futureRoundId.getRoundNumber(), 60);

    roles.getProposer().injectProposal(roundId, initialBlock);
    for (final ValidatorPeer peer : roles.getNonProposingPeers()) {
      peer.injectPrepare(roundId, initialBlock.getHash());
    }
    roles.getProposer().injectCommit(roundId, initialBlock.getHash());
    // At this stage, the local node has 2 commit msgs (proposer and local) so has not committed
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);

    for (final ValidatorPeer peer : roles.getAllPeers()) {
      peer.clearReceivedMessages();
    }

    injectEmptyNewRound(
        futureRoundId, futureRoles.getProposer(), futureRoles.getAllPeers(), futureBlock);

    final SignedData<PreparePayload> expectedFuturePrepare =
        localNodeMessageFactory.createSignedPreparePayload(futureRoundId, futureBlock.getHash());
    assertPeersReceivedExactly(roles.getAllPeers(), expectedFuturePrepare);

    // attempt to complete the previous round
    roles.getNonProposingPeers().get(0).injectCommit(roundId, initialBlock.getHash());
    assertPeersReceivedNoMessages(roles.getAllPeers());
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
  }
}
