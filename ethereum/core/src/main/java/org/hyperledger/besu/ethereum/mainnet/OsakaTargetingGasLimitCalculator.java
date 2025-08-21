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

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import org.apache.tuweni.units.bigints.UInt256;

public class OsakaTargetingGasLimitCalculator extends CancunTargetingGasLimitCalculator {
  /** The constant max number of blobs per transaction defined for Osaka */
  private static final int DEFAULT_MAX_BLOBS_PER_TRANSACTION = 6;

  /** The blob base cost constant for Osaka */
  private static final long BLOB_BASE_COST = 1 << 13; // 2^13

  /** The mainnet transaction gas limit cap for Osaka */
  private static final long DEFAULT_TRANSACTION_GAS_LIMIT_CAP_OSAKA = 16_777_216L;

  private final long transactionGasLimitCap;
  private final long transactionBlobGasLimitCap;

  public OsakaTargetingGasLimitCalculator(
      final long londonForkBlock,
      final BaseFeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final int maxBlobsPerBlock,
      final int targetBlobsPerBlock) {
    this(
        londonForkBlock,
        feeMarket,
        gasCalculator,
        maxBlobsPerBlock,
        targetBlobsPerBlock,
        DEFAULT_MAX_BLOBS_PER_TRANSACTION,
        DEFAULT_TRANSACTION_GAS_LIMIT_CAP_OSAKA);
  }

  public OsakaTargetingGasLimitCalculator(
      final long londonForkBlock,
      final BaseFeeMarket feeMarket,
      final GasCalculator gasCalculator,
      final int maxBlobsPerBlock,
      final int targetBlobsPerBlock,
      final int maxBlobsPerTransaction,
      final long transactionGasLimitCap) {
    super(londonForkBlock, feeMarket, gasCalculator, maxBlobsPerBlock, targetBlobsPerBlock);
    this.transactionGasLimitCap = transactionGasLimitCap;
    this.transactionBlobGasLimitCap = gasCalculator.getBlobGasPerBlob() * maxBlobsPerTransaction;
    checkArgument(
        maxBlobsPerBlock >= maxBlobsPerTransaction,
        "maxBlobsPerTransaction (%s) must not be greater than maxBlobsPerBlock (%s)",
        maxBlobsPerTransaction,
        maxBlobsPerBlock);
  }

  @Override
  public long transactionGasLimitCap() {
    return transactionGasLimitCap;
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
    Wei blobGasBaseFee = feeMarket.blobGasPricePerGas(BlobGas.of(parentExcessBlobGas));

    UInt256 parentBaseFee = UInt256.valueOf(parentBaseFeePerGas);
    UInt256 blobGasPerBlob = UInt256.valueOf(getBlobGasPerBlob());

    UInt256 baseFeeCostPerBlob = parentBaseFee.multiply(UInt256.valueOf(BLOB_BASE_COST));
    UInt256 effectiveBlobGasCost = blobGasBaseFee.toUInt256().multiply(blobGasPerBlob);

    if (baseFeeCostPerBlob.compareTo(effectiveBlobGasCost) > 0) {
      return parentExcessBlobGas
          + parentBlobGasUsed * (maxBlobsPerBlock - targetBlobsPerBlock) / maxBlobsPerBlock;
    } else {
      // same as Cancun
      return currentExcessBlobGas - getTargetBlobGasPerBlock();
    }
  }

  @Override
  public long transactionBlobGasLimitCap() {
    return transactionBlobGasLimitCap;
  }
}
