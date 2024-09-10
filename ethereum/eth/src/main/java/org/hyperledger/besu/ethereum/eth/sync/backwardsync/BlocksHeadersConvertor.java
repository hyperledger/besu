/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLP;

import org.apache.tuweni.bytes.Bytes;

public class BlocksHeadersConvertor implements ValueConvertor<BlockHeader> {
  private final BlockHeaderFunctions blockHeaderFunctions;

  public BlocksHeadersConvertor(final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
  }

  public static ValueConvertor<BlockHeader> of(final BlockHeaderFunctions blockHeaderFunctions) {
    return new BlocksHeadersConvertor(blockHeaderFunctions);
  }

  @Override
  public BlockHeader fromBytes(final byte[] bytes) {
    return BlockHeader.readFrom(RLP.input(Bytes.wrap(bytes)), blockHeaderFunctions);
  }

  @Override
  public byte[] toBytes(final BlockHeader value) {
    BytesValueRLPOutput output = new BytesValueRLPOutput();
    value.writeTo(output);
    return output.encoded().toArrayUnsafe();
  }
}
