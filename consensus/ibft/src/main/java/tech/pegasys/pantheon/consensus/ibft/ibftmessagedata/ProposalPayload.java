/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.ibftmessagedata;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

import java.util.Objects;
import java.util.StringJoiner;

public class ProposalPayload implements InRoundPayload {
  private static final int TYPE = IbftV2.PROPOSAL;
  private final ConsensusRoundIdentifier roundIdentifier;
  private final Block block;

  public ProposalPayload(final ConsensusRoundIdentifier roundIdentifier, final Block block) {
    this.roundIdentifier = roundIdentifier;
    this.block = block;
  }

  public static ProposalPayload readFrom(final RLPInput rlpInput) {
    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);
    final Block block =
        Block.readFrom(rlpInput, IbftBlockHashing::calculateDataHashForCommittedSeal);
    rlpInput.leaveList();

    return new ProposalPayload(roundIdentifier, block);
  }

  @Override
  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.startList();
    roundIdentifier.writeTo(rlpOutput);
    block.writeTo(rlpOutput);
    rlpOutput.endList();
  }

  public Block getBlock() {
    return block;
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
    final ProposalPayload that = (ProposalPayload) o;
    return Objects.equals(roundIdentifier, that.roundIdentifier)
        && Objects.equals(block, that.block);
  }

  @Override
  public int hashCode() {
    return Objects.hash(roundIdentifier, block);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", ProposalPayload.class.getSimpleName() + "[", "]")
        .add("roundIdentifier=" + roundIdentifier)
        .add("block=" + block)
        .toString();
  }
}
