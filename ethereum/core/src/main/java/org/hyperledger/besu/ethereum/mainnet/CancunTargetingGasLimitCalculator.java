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
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

public class CancunTargetingGasLimitCalculator extends LondonTargetingGasLimitCalculator {

  /** The mainnet default maximum number of blobs per block for Cancun */
  private static final int DEFAULT_MAX_BLOBS_PER_BLOCK_CANCUN = 6;

  private final long maxBlobGasPerBlock;

  public CancunTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    this(londonForkBlock, feeMarket, DEFAULT_MAX_BLOBS_PER_BLOCK_CANCUN);
  }

  /**
   * Using Cancun mainnet default of 6 blobs for maxBlobsPerBlock: getBlobGasPerBlob() * 6 blobs =
   * 131072 * 6 = 786432 = 0xC0000
   */
  public CancunTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket, final int maxBlobsPerBlock) {
    super(londonForkBlock, feeMarket);
    final CancunGasCalculator cancunGasCalculator = new CancunGasCalculator();
    this.maxBlobGasPerBlock = cancunGasCalculator.getBlobGasPerBlob() * maxBlobsPerBlock;
  }

  @Override
  public long currentBlobGasLimit() {
    return maxBlobGasPerBlock;
  }
}
