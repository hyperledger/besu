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
package org.hyperledger.besu.consensus.qbft.core.support;

import static org.hyperledger.besu.consensus.qbft.core.support.IntegrationTestHelpers.createCommitBlockFromProposalBlock;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.EventMultiplexer;
import org.hyperledger.besu.consensus.common.bft.inttest.DefaultValidatorPeer;
import org.hyperledger.besu.consensus.common.bft.inttest.NodeParams;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.core.statemachine.PreparedCertificate;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

// Each "inject" function returns the SignedPayload representation of the transmitted message.
public class ValidatorPeer extends DefaultValidatorPeer {

  private final MessageFactory messageFactory;

  public ValidatorPeer(
      final NodeParams nodeParams,
      final MessageFactory messageFactory,
      final EventMultiplexer localEventMultiplexer) {
    super(nodeParams, localEventMultiplexer);
    this.messageFactory = messageFactory;
  }

  public Proposal injectProposal(final ConsensusRoundIdentifier rId, final Block block) {
    return injectProposalForFutureRound(
        rId, Collections.emptyList(), Collections.emptyList(), block);
  }

  public Prepare injectPrepare(final ConsensusRoundIdentifier rId, final Hash digest) {
    final Prepare payload = messageFactory.createPrepare(rId, digest);
    injectMessage(PrepareMessageData.create(payload));
    return payload;
  }

  public Commit injectCommit(final ConsensusRoundIdentifier rId, final Block block) {
    final Block commitBlock = createCommitBlockFromProposalBlock(block, rId.getRoundNumber());
    final SECPSignature commitSeal = nodeKey.sign(commitBlock.getHash());
    return injectCommit(rId, block.getHash(), commitSeal);
  }

  public Commit injectCommit(
      final ConsensusRoundIdentifier rId, final Hash digest, final SECPSignature commitSeal) {
    final Commit payload = messageFactory.createCommit(rId, digest, commitSeal);
    injectMessage(CommitMessageData.create(payload));
    return payload;
  }

  public Proposal injectProposalForFutureRound(
      final ConsensusRoundIdentifier rId,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares,
      final Block blockToPropose) {

    final Proposal payload =
        messageFactory.createProposal(rId, blockToPropose, roundChanges, prepares);
    injectMessage(ProposalMessageData.create(payload));
    return payload;
  }

  public RoundChange injectRoundChange(
      final ConsensusRoundIdentifier rId,
      final Optional<PreparedCertificate> preparedRoundArtifacts) {
    final RoundChange payload = messageFactory.createRoundChange(rId, preparedRoundArtifacts);
    injectMessage(RoundChangeMessageData.create(payload));
    return payload;
  }

  public MessageFactory getMessageFactory() {
    return messageFactory;
  }
}
