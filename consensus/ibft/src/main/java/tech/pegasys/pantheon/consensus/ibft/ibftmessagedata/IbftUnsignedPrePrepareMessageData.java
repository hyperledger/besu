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

// NOTE: Implementation of all methods of this class is still pending. This class was added to show
// how a PreparedCertificate is encoded and decoded inside a RoundChange message
public class IbftUnsignedPrePrepareMessageData extends AbstractIbftUnsignedInRoundMessageData {

  private static final int TYPE = IbftV2.PRE_PREPARE;
  private final Block block;

  public IbftUnsignedPrePrepareMessageData(
      final ConsensusRoundIdentifier roundIdentifier, final Block block) {
    super(roundIdentifier);
    this.block = block;
  }

  public static IbftUnsignedPrePrepareMessageData readFrom(final RLPInput rlpInput) {

    rlpInput.enterList();
    final ConsensusRoundIdentifier roundIdentifier = ConsensusRoundIdentifier.readFrom(rlpInput);
    final Block block =
        Block.readFrom(rlpInput, IbftBlockHashing::calculateDataHashForCommittedSeal);
    rlpInput.leaveList();

    return new IbftUnsignedPrePrepareMessageData(roundIdentifier, block);
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
}
