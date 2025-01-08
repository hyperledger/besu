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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

public class PragueTargetingGasLimitCalculator extends CancunTargetingGasLimitCalculator {

  /** The mainnet default maximum number of blobs per block for Prague */
  private static final int DEFAULT_MAX_BLOBS_PER_BLOCK_PRAGUE = 9;

  public PragueTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    super(londonForkBlock, feeMarket, DEFAULT_MAX_BLOBS_PER_BLOCK_PRAGUE);
  }

  /**
   * Using Prague mainnet default of 9 blobs for maxBlobsPerBlock:
   * CancunGasCalculator.BLOB_GAS_PER_BLOB * 9 blobs = 131072 * 9 = 1179648 = 0x120000
   */
  public PragueTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket, final int maxBlobsPerBlock) {
    super(londonForkBlock, feeMarket, maxBlobsPerBlock);
  }
}
