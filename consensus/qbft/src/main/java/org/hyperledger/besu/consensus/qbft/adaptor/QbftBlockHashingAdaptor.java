/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHashing;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.datatypes.Hash;

/** Adaptor to allow a {linkBftBlockHashing} to be used as a {@link QbftBlockHashing}. */
public class QbftBlockHashingAdaptor implements QbftBlockHashing {
  private final BftBlockHashing bftBlockHashing;

  /**
   * Construct a new Qbft BlockHasher
   *
   * @param bftBlockHashing the BFT BlockHashing
   */
  public QbftBlockHashingAdaptor(final BftBlockHashing bftBlockHashing) {
    this.bftBlockHashing = bftBlockHashing;
  }

  @Override
  public Hash calculateDataHashForCommittedSeal(
      final QbftBlockHeader header, final BftExtraData extraData) {
    return bftBlockHashing.calculateDataHashForCommittedSeal(
        BlockUtil.toBesuBlockHeader(header), extraData);
  }
}
