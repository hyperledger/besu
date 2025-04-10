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

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

import java.util.Objects;

import com.google.common.base.MoreObjects;

/** The Proposal payload. */
public class ProposalPayload extends QbftPayload {

  private static final int TYPE = QbftV1.PROPOSAL;
  private final ConsensusRoundIdentifier roundIdentifier;
  private final QbftBlock proposedBlock;
  private final QbftBlockCodec blockEncoder;

  /**
   * Instantiates a new Proposal payload.
   *
   * @param roundIdentifier the round identifier
   * @param proposedBlock the proposed block
   * @param blockEncoder the qbft block encoder
   */
  public ProposalPayload(
      final ConsensusRoundIdentifier roundIdentifier,
      final QbftBlock proposedBlock,
      final QbftBlockCodec blockEncoder) {
    this.roundIdentifier = roundIdentifier;
    this.proposedBlock = proposedBlock;
    this.blockEncoder = blockEncoder;
  }

  /**
   * Read from rlp input and return proposal payload.
   *
   * @param rlpInput the rlp input
   * @param blockEncoder the qbft block encoder
   * @return the proposal payload
   */
  public static ProposalPayload readFrom(
      final RLPInput rlpInput, final QbftBlockCodec blockEncoder) {
    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = readConsensusRound(rlpInput);
    final QbftBlock proposedBlock = blockEncoder.readFrom(rlpInput);
    rlpInput.leaveList();

    return new ProposalPayload(roundIdentifier, proposedBlock, blockEncoder);
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    writeConsensusRound(rlpOutput);
    blockEncoder.writeTo(proposedBlock, rlpOutput);
    rlpOutput.endList();
  }

  /**
   * Gets proposed block.
   *
   * @return the proposed block
   */
  public QbftBlock getProposedBlock() {
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
