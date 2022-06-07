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
package org.hyperledger.besu.consensus.ibftlegacy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;

public class LegacyIbftBlockHeaderFunctions implements BlockHeaderFunctions {

  @Override
  public Hash hash(final BlockHeader header) {
    return IbftBlockHashing.calculateHashOfIbftBlockOnchain(header);
  }

  @Override
  public IbftExtraData parseExtraData(final BlockHeader header) {
    return IbftExtraData.decodeRaw(header.getExtraData());
  }

  @Override
  public int getCheckPointWindowSize(final BlockHeader header) {
    return IbftExtraData.decodeRaw(header.getExtraData()).getValidators().size();
  }
}
