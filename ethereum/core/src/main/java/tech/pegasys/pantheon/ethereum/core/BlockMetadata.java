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
package tech.pegasys.pantheon.ethereum.core;

import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

public class BlockMetadata {
  private static final BlockMetadata EMPTY = new BlockMetadata(null);
  private final UInt256 totalDifficulty;

  public BlockMetadata(final UInt256 totalDifficulty) {
    this.totalDifficulty = totalDifficulty;
  }

  public static BlockMetadata empty() {
    return EMPTY;
  }

  public static BlockMetadata fromRlp(final BytesValue bytes) {
    return readFrom(RLP.input(bytes));
  }

  public static BlockMetadata readFrom(final RLPInput in) throws RLPException {
    in.enterList();

    final UInt256 totalDifficulty = in.readUInt256Scalar();

    in.leaveList();

    return new BlockMetadata(totalDifficulty);
  }

  public UInt256 getTotalDifficulty() {
    return totalDifficulty;
  }

  public BytesValue toRlp() {
    return RLP.encode(this::writeTo);
  }

  public void writeTo(final RLPOutput out) {
    out.startList();

    out.writeUInt256Scalar(totalDifficulty);

    out.endList();
  }
}
