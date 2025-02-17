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

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftHashMode;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;

/** Utility class to get the correct BlockHeaderFunctions based on the QbftHashMode. */
public class BlockHeaderFunctionsUtil {
  // Private constructor to prevent instantiation
  private BlockHeaderFunctionsUtil() {}

  /**
   * Get the correct BlockHeaderFunctions based on the QbftHashMode.
   *
   * @param extraDataCodec the extra data codec
   * @param hashMode the hash mode
   * @return the block header functions
   */
  public static BlockHeaderFunctions getBlockHeaderFunctions(
      final QbftExtraDataCodec extraDataCodec, final QbftHashMode hashMode) {
    if (hashMode == QbftHashMode.ONCHAIN) {
      return BftBlockHeaderFunctions.forOnchainBlock(extraDataCodec);
    } else if (hashMode == QbftHashMode.COMMITTED_SEAL) {
      return BftBlockHeaderFunctions.forCommittedSeal(extraDataCodec);
    } else {
      throw new IllegalStateException("Invalid HashMode");
    }
  }
}
