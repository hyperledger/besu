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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.qbft.support.IntegrationTestHelpers.createCommitBlockFromProposalBlock;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.support.RoundSpecificPeers;
import org.hyperledger.besu.consensus.qbft.support.TestContext;
import org.hyperledger.besu.consensus.qbft.support.TestContextBuilder;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

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
          .buildAndStart();

  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificPeers peers = context.roundSpecificPeers(roundId);

  private final ConsensusRoundIdentifier futureRoundId = new ConsensusRoundIdentifier(1, 5);
  private final RoundSpecificPeers futurePeers = context.roundSpecificPeers(futureRoundId);

  private final MessageFactory localNodeMessageFactory = context.getLocalNodeMessageFactory();

  @Test
  public void messagesForFutureRoundAreNotActionedUntilRoundIsActive() {
    final Block futureBlock =
        context.createBlockForProposalFromChainHead(60, peers.getProposer().getNodeAddress());
    final int quorum = BftHelpers.calculateRequiredValidatorQuorum(NETWORK_SIZE);
    final ConsensusRoundIdentifier subsequentRoundId = new ConsensusRoundIdentifier(1, 6);
    final RoundSpecificPeers subsequentRoles = context.roundSpecificPeers(subsequentRoundId);

    for (int i = 0; i < quorum - 2; i++) {
      futurePeers.getNonProposing(i).injectPrepare(futureRoundId, futureBlock.getHash());
    }

    for (int i = 0; i < quorum - 2; i++) {
      futurePeers.getNonProposing(i).injectCommit(futureRoundId, futureBlock);
    }

    // inject a prepare and a commit from a subsequent round, and ensure no transmissions are
    // created

    subsequentRoles.getNonProposing(1).injectPrepare(subsequentRoundId, futureBlock.getHash());
    subsequentRoles.getNonProposing(1).injectCommit(subsequentRoundId, futureBlock);

    peers.verifyNoMessagesReceived();
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);

    // inject a newRound to move to 'futureRoundId', and ensure localnode sends prepare, commit
    // and updates blockchain
    final List<SignedData<RoundChangePayload>> signedRoundChangePayload =
        futurePeers.createSignedRoundChangePayload(futureRoundId);
    futurePeers
        .getProposer()
        .injectProposalForFutureRound(
            futureRoundId, signedRoundChangePayload, emptyList(), futureBlock);

    final Prepare expectedPrepare =
        localNodeMessageFactory.createPrepare(futureRoundId, futureBlock.getHash());

    peers.verifyMessagesReceived(expectedPrepare);

    // following 1 more prepare, a commit msg will be sent
    futurePeers.getNonProposing(quorum - 2).injectPrepare(futureRoundId, futureBlock.getHash());

    final Block commitBlock =
        createCommitBlockFromProposalBlock(futureBlock, futureRoundId.getRoundNumber());
    final SECPSignature commitSeal =
        context.getLocalNodeParams().getNodeKey().sign(commitBlock.getHash());
    final Commit expectedCommit =
        localNodeMessageFactory.createCommit(futureRoundId, futureBlock.getHash(), commitSeal);
    peers.verifyMessagesReceived(expectedCommit);

    // requires 1 more commit and the blockchain will progress
    futurePeers.getNonProposing(quorum - 2).injectCommit(futureRoundId, futureBlock);

    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(1);
  }

  @Test
  public void priorRoundsCannotBeCompletedAfterReceptionOfNewRound() {
    final Block initialBlock =
        context.createBlockForProposalFromChainHead(30, peers.getProposer().getNodeAddress());
    final Block futureBlock =
        context.createBlockForProposalFromChainHead(60, peers.getProposer().getNodeAddress());

    peers.getProposer().injectProposal(roundId, initialBlock);

    peers.prepareForNonProposing(roundId, initialBlock.getHash());

    peers.getProposer().injectCommit(roundId, initialBlock);
    // At this stage, the local node has 2 commit msgs (proposer and local) so has not committed
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);

    peers.clearReceivedMessages();

    final List<SignedData<RoundChangePayload>> signedRoundChangePayload =
        futurePeers.createSignedRoundChangePayload(futureRoundId);
    futurePeers
        .getProposer()
        .injectProposalForFutureRound(
            futureRoundId, signedRoundChangePayload, emptyList(), futureBlock);

    final Prepare expectedFuturePrepare =
        localNodeMessageFactory.createPrepare(futureRoundId, futureBlock.getHash());
    peers.verifyMessagesReceived(expectedFuturePrepare);

    // attempt to complete the previous round
    peers.getNonProposing(0).injectCommit(roundId, initialBlock);
    peers.verifyNoMessagesReceived();
    assertThat(context.getBlockchain().getChainHeadBlockNumber()).isEqualTo(0);
  }
}
