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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.function.Function;

public class BftBlockHeaderFunctions implements BlockHeaderFunctions {

  private final Function<BlockHeader, Hash> hashFunction;
  private final BftExtraDataEncoder bftExtraDataEncoder;

  public BftBlockHeaderFunctions(
      final Function<BlockHeader, Hash> hashFunction,
      final BftExtraDataEncoder bftExtraDataEncoder) {
    this.hashFunction = hashFunction;
    this.bftExtraDataEncoder = bftExtraDataEncoder;
  }

  public static BlockHeaderFunctions forOnChainBlock(
      final BftExtraDataEncoder bftExtraDataEncoder) {
    return new BftBlockHeaderFunctions(
        h -> new BftBlockHashing(bftExtraDataEncoder).calculateHashOfBftBlockOnChain(h),
        bftExtraDataEncoder);
  }

  public static BlockHeaderFunctions forCommittedSeal(
      final BftExtraDataEncoder bftExtraDataEncoder) {
    return new BftBlockHeaderFunctions(
        h -> new BftBlockHashing(bftExtraDataEncoder).calculateDataHashForCommittedSeal(h),
        bftExtraDataEncoder);
  }

  @Override
  public Hash hash(final BlockHeader header) {
    return hashFunction.apply(header);
  }

  @Override
  public BftExtraData parseExtraData(final BlockHeader header) {
    return bftExtraDataEncoder.decodeRaw(header.getExtraData());
  }
}
