/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import org.apache.tuweni.bytes.Bytes;

public class BlocksConvertor implements ValueConvertor<Block> {
  private final BlockHeaderFunctions blockHeaderFunctions;

  public BlocksConvertor(final BlockHeaderFunctions blockHeaderFunctions) {
    this.blockHeaderFunctions = blockHeaderFunctions;
  }

  public static ValueConvertor<Block> of(final BlockHeaderFunctions blockHeaderFunctions) {
    return new BlocksConvertor(blockHeaderFunctions);
  }

  @Override
  public Block fromBytes(final byte[] bytes) {

    final RLPInput input = RLP.input(Bytes.wrap(bytes));
    return Block.readFrom(input, blockHeaderFunctions);
  }

  @Override
  public byte[] toBytes(final Block value) {
    return value.toRlp().toArrayUnsafe();
  }
}
