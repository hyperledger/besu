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
package org.hyperledger.besu.consensus.qbft.core.payload;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/** The Proposal payload. */
public class ProposalPayload extends QbftPayload {

  private static final int TYPE = QbftV1.PROPOSAL;
  private final ConsensusRoundIdentifier roundIdentifier;
  private final Block proposedBlock;

  /**
   * Instantiates a new Proposal payload.
   *
   * @param roundIdentifier the round identifier
   * @param proposedBlock the proposed block
   */
  public ProposalPayload(
      final ConsensusRoundIdentifier roundIdentifier, final Block proposedBlock) {
    this.roundIdentifier = roundIdentifier;
    this.proposedBlock = proposedBlock;
  }

  /**
   * Read from rlp input and return proposal payload.
   *
   * @param rlpInput the rlp input
   * @param bftExtraDataCodec the bft extra data codec
   * @return the proposal payload
   */
  public static ProposalPayload readFrom(
      final RLPInput rlpInput, final BftExtraDataCodec bftExtraDataCodec) {
    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = readConsensusRound(rlpInput);
    final Block proposedBlock =
        Block.readFrom(rlpInput, BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    rlpInput.leaveList();

    return new ProposalPayload(roundIdentifier, proposedBlock);
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    writeConsensusRound(rlpOutput);
    proposedBlock.writeTo(rlpOutput);
    rlpOutput.endList();
  }

  /**
   * Gets proposed block.
   *
   * @return the proposed block
   */
  public Block getProposedBlock() {
    return proposedBlock;
  }

  @Override
  public int getMessageType() {
    return TYPE;
  }

  @Override
  public ConsensusRoundIdentifier getRoundIdentifier() {
    return roundIdentifier;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProposalPayload that = (ProposalPayload) o;
    return Objects.equals(roundIdentifier, that.roundIdentifier)
        && Objects.equals(proposedBlock, that.proposedBlock);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundIdentifier, proposedBlock);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("roundIdentifier", roundIdentifier)
        .add("proposedBlock", proposedBlock)
        .toString();
  }
}
