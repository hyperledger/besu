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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.common.ValidatorVote;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;

public enum IbftVoteType implements ValidatorVote {
  ADD((byte) 0xFF),
  DROP((byte) 0x00);

  private final byte voteValue;

  IbftVoteType(final byte voteValue) {
    this.voteValue = voteValue;
  }

  public byte getVoteValue() {
    return voteValue;
  }

  public static IbftVoteType readFrom(final RLPInput rlpInput) {
    final byte encodedByteValue = rlpInput.readByte();
    for (final IbftVoteType voteType : values()) {
      if (voteType.voteValue == encodedByteValue) {
        return voteType;
      }
    }

    throw new RLPException("Invalid IbftVoteType RLP encoding");
  }

  public void writeTo(final RLPOutput rlpOutput) {
    rlpOutput.writeByte(voteValue);
  }

  @Override
  public boolean isAddVote() {
    return this.equals(ADD);
  }

  @Override
  public boolean isDropVote() {
    return this.equals(DROP);
  }
}
