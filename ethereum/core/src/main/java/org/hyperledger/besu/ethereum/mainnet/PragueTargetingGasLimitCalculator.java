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
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;

import org.apache.tuweni.units.bigints.UInt64;

public class PragueTargetingGasLimitCalculator extends CancunTargetingGasLimitCalculator {

  private final UInt64 maxBlobsPerBlock;

  /**
   * Used at configuration-time for bootstrapping future versions of this class. maxBlobsPerBlock
   * will only be available at runtime, where the other constructor will be used Default of Cancun
   * value is used here but must be replaced
   *
   * @param londonForkBlock The block number of the London hard fork.
   * @param feeMarket The fee market to use for gas limit calculations.
   */
  public PragueTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    super(londonForkBlock, feeMarket);
    this.maxBlobsPerBlock = UInt64.valueOf(CancunTargetingGasLimitCalculator.MAX_BLOBS_PER_BLOCK);
  }

  /**
   * Used to support a dynamic maxBlobsPerBlock received at runtime
   *
   * @param gasLimitCalculator The gas limit calculator to copy.
   * @param maxBlobsPerBlock The maximum number of blobs that can be included in a block.
   */
  public PragueTargetingGasLimitCalculator(
      final PragueTargetingGasLimitCalculator gasLimitCalculator, final UInt64 maxBlobsPerBlock) {
    super(gasLimitCalculator);
    this.maxBlobsPerBlock = maxBlobsPerBlock;
  }

  @Override
  public long currentBlobGasLimit() {
    return new PragueGasCalculator().blobGasCost(maxBlobsPerBlock.toLong());
  }
}
