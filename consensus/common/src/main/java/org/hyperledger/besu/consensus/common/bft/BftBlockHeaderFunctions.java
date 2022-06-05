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
package org.hyperledger.besu.consensus.common.bft;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;

import java.util.function.Function;

public class BftBlockHeaderFunctions implements BlockHeaderFunctions {

  private final Function<BlockHeader, Hash> hashFunction;
  private final BftExtraDataCodec bftExtraDataCodec;

  public BftBlockHeaderFunctions(
      final Function<BlockHeader, Hash> hashFunction, final BftExtraDataCodec bftExtraDataCodec) {
    this.hashFunction = hashFunction;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  public static BlockHeaderFunctions forOnchainBlock(final BftExtraDataCodec bftExtraDataCodec) {
    return new BftBlockHeaderFunctions(
        h -> new BftBlockHashing(bftExtraDataCodec).calculateHashOfBftBlockOnchain(h),
        bftExtraDataCodec);
  }

  public static BlockHeaderFunctions forCommittedSeal(final BftExtraDataCodec bftExtraDataCodec) {
    return new BftBlockHeaderFunctions(
        h -> new BftBlockHashing(bftExtraDataCodec).calculateDataHashForCommittedSeal(h),
        bftExtraDataCodec);
  }

  @Override
  public Hash hash(final BlockHeader header) {
    return hashFunction.apply(header);
  }

  @Override
  public BftExtraData parseExtraData(final BlockHeader header) {
    return bftExtraDataCodec.decodeRaw(header.getExtraData());
  }

  @Override
  public int getCheckPointWindowSize(final BlockHeader header) {
    return bftExtraDataCodec.decodeRaw(header.getExtraData()).getValidators().size();
  }
}
