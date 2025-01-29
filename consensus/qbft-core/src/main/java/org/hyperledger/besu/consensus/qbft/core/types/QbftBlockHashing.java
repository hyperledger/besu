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
package org.hyperledger.besu.consensus.qbft.core.types;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.datatypes.Hash;

/** Provides hashing for QBFT block headers. */
public interface QbftBlockHashing {

  /**
   * Constructs a hash of the block header suitable for signing as a committed seal. The extra data
   * in the hash uses an empty list for the committed seals.
   *
   * @param header The header for which a proposer seal is to be calculated (with or without extra
   *     data)
   * @param extraData The extra data block which is to be inserted to the header once seal is
   *     calculated
   * @return the hash of the header including the validator and proposer seal in the extra data
   */
  Hash calculateDataHashForCommittedSeal(QbftBlockHeader header, BftExtraData extraData);
}
