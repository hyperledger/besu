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
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CancunTargetingGasLimitCalculator extends LondonTargetingGasLimitCalculator {
  private final long maxBlobGasPerBlock;
  private final long targetBlobGasPerBlock;
  private final long blobGasPerBlob;

  protected final int maxBlobsPerBlock;
  protected final int targetBlobsPerBlock;

  /**
   * Using Cancun mainnet default of 6 blobs for maxBlobsPerBlock: getBlobGasPerBlob() * 6 blobs =
   * 131072 * 6 = 786432 = 0xC0000
   */
  public CancunTargetingGasLimitCalculator(
      final long londonForkBlock,
      final BaseFeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final int maxBlobsPerBlock,
      final int targetBlobsPerBlock) {
    super(londonForkBlock, feeMarket);
    this.blobGasPerBlob = gasCalculator.getBlobGasPerBlob();
    this.targetBlobGasPerBlock = blobGasPerBlob * targetBlobsPerBlock;
    this.maxBlobGasPerBlock = blobGasPerBlob * maxBlobsPerBlock;
    this.maxBlobsPerBlock = maxBlobsPerBlock;
    this.targetBlobsPerBlock = targetBlobsPerBlock;
  }

  @Override
  public long currentBlobGasLimit() {
    return getMaxBlobGasPerBlock();
  }

  @Override
  public long computeExcessBlobGas(
      final long parentExcessBlobGas,
      final long parentBlobGasUsed,
      final long parentBaseFeePerGas) {
    final long currentExcessBlobGas = parentExcessBlobGas + parentBlobGasUsed;
    if (currentExcessBlobGas < targetBlobGasPerBlock) {
      return 0L;
    }
    return currentExcessBlobGas - targetBlobGasPerBlock;
  }

  public long getBlobGasPerBlob() {
    return blobGasPerBlob;
  }

  /**
   * Retrieves the target blob gas per block.
   *
   * @return The target blob gas per block.
   */
  @Override
  public long getTargetBlobGasPerBlock() {
    return targetBlobGasPerBlock;
  }

  public long getMaxBlobGasPerBlock() {
    return maxBlobGasPerBlock;
  }

  @Override
  public long transactionBlobGasLimitCap() {
    return maxBlobGasPerBlock;
  }
}
