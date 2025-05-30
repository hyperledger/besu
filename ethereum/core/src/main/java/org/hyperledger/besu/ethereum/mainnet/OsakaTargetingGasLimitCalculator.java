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
      final GasCalculator gasCalculator) {
    super(londonForkBlock, feeMarket, gasCalculator);
  }

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

    System.out.println(
        "parentExcessBlobGas = "
            + parentExcessBlobGas
            + ", parentBlobGasUsed = "
            + parentBlobGasUsed
            + ", parentBaseFeePerGas = "
            + parentBaseFeePerGas);
    System.out.println("currentExcessBlobGas = " + currentExcessBlobGas);
    System.out.println("getTargetBlobGasPerBlock() = " + getTargetBlobGasPerBlock());

    // First check if we're below the target
    if (currentExcessBlobGas < getTargetBlobGasPerBlock()) {
      return 0L;
    }

    // Calculate base fee per blob gas using the parent excess blob gas
    Wei baseFeeBlobGas = Wei.ZERO;
    if (getFeeMarket().implementsBlobFee()) {
      baseFeeBlobGas = getFeeMarket().blobGasPricePerGas(BlobGas.of(parentExcessBlobGas));
    }
    long baseFeeBlobGasLong = baseFeeBlobGas.toLong();
    System.out.println("baseFeeBlobGas = " + baseFeeBlobGas);
    System.out.println("baseFeeBlobGasLong = " + baseFeeBlobGasLong);
    //    if BLOB_BASE_COST * parent.base_fee_per_gas > GAS_PER_BLOB *
    // get_base_fee_per_blob_gas(parent):
    //        return parent.excess_blob_gas + parent.blob_gas_used * (blobSchedule.max -
    // blobSchedule.target) // blobSchedule.max
    if (BLOB_BASE_COST * parentBaseFeePerGas > getBlobGasPerBlob() * baseFeeBlobGasLong) {
      System.out.println("getBlobGasPerBlob() *" + getBlobGasPerBlob());
      System.out.println("getTargetBlobsPerBlock() *" + getTargetBlobsPerBlock());
      System.out.println("getMaxBlobsPerBlock() *" + getMaxBlobsPerBlock());

      System.out.println(
          "return 2nd option = "
              + currentExcessBlobGas
              + "*"
              + +(getMaxBlobsPerBlock() - getTargetBlobsPerBlock()) / getMaxBlobsPerBlock());
      return currentExcessBlobGas
          * (getMaxBlobsPerBlock() - getTargetBlobsPerBlock())
          / getMaxBlobsPerBlock();

    } else {
      System.out.println(
          "return 3rd option = " + (currentExcessBlobGas - getTargetBlobGasPerBlock()));
      return currentExcessBlobGas - getTargetBlobGasPerBlock();
    }
  }
}
