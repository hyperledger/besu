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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class OsakaTargetingGasLimitCalculator extends CancunTargetingGasLimitCalculator {

  /** The blob base cost constant for Osaka */
  private static final long BLOB_BASE_COST = 1 << 14; // 2^14

  public OsakaTargetingGasLimitCalculator(
      final long londonForkBlock,
      final BaseFeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final int maxBlobsPerBlock,
      final int targetBlobsPerBlock) {
    super(londonForkBlock, feeMarket, gasCalculator, maxBlobsPerBlock, targetBlobsPerBlock);
  }

  @Override
  public long computeExcessBlobGas(
      final long parentExcessBlobGas,
      final long parentBlobGasUsed,
      final long parentBaseFeePerGas) {
    final long currentExcessBlobGas = parentExcessBlobGas + parentBlobGasUsed;

    // First check if we're below the target
    if (currentExcessBlobGas < getTargetBlobGasPerBlock()) {
      return 0L;
    }

    // EIP-7918 https://eips.ethereum.org/EIPS/eip-7918
    Wei baseFeeBlobGas = Wei.ZERO;
    if (feeMarket.implementsBlobFee()) {
      baseFeeBlobGas = feeMarket.blobGasPricePerGas(BlobGas.of(parentExcessBlobGas));
    }
    long baseFeeBlobGasLong = baseFeeBlobGas.toLong();
    if (BLOB_BASE_COST * parentBaseFeePerGas > getBlobGasPerBlob() * baseFeeBlobGasLong) {
      return parentExcessBlobGas
          + parentBlobGasUsed
              * (getMaxBlobsPerBlock() - getTargetBlobsPerBlock())
              / getMaxBlobsPerBlock();
    } else {
      // same as Cancun
      return currentExcessBlobGas - getTargetBlobGasPerBlock();
    }
  }

  public int getMaxBlobsPerBlock() {
    return maxBlobsPerBlock;
  }

  public int getTargetBlobsPerBlock() {
    return targetBlobsPerBlock;
  }
}
