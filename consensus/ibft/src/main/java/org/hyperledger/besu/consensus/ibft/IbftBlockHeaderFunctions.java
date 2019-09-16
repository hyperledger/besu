/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.consensus.ibft;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.function.Function;

public class IbftBlockHeaderFunctions implements BlockHeaderFunctions {

  private static final IbftBlockHeaderFunctions COMMITTED_SEAL =
      new IbftBlockHeaderFunctions(IbftBlockHashing::calculateDataHashForCommittedSeal);
  private static final IbftBlockHeaderFunctions ON_CHAIN =
      new IbftBlockHeaderFunctions(IbftBlockHashing::calculateHashOfIbftBlockOnChain);

  private final Function<BlockHeader, Hash> hashFunction;

  private IbftBlockHeaderFunctions(final Function<BlockHeader, Hash> hashFunction) {
    this.hashFunction = hashFunction;
  }

  public static BlockHeaderFunctions forOnChainBlock() {
    return ON_CHAIN;
  }

  public static BlockHeaderFunctions forCommittedSeal() {
    return COMMITTED_SEAL;
  }

  @Override
  public Hash hash(final BlockHeader header) {
    return hashFunction.apply(header);
  }

  @Override
  public IbftExtraData parseExtraData(final BlockHeader header) {
    return IbftExtraData.decodeRaw(header.getExtraData());
  }
}
