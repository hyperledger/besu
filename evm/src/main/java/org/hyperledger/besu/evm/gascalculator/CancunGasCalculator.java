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
package org.hyperledger.besu.evm.gascalculator;

import static org.hyperledger.besu.datatypes.Address.KZG_POINT_EVAL;

/**
 * Gas Calculator for Cancun
 *
 * <UL>
 *   <LI>Gas costs for TSTORE/TLOAD
 *   <LI>Blob gas for EIP-4844
 * </UL>
 */
public class CancunGasCalculator extends ShanghaiGasCalculator {

  /** The default mainnet target blobs per block for Cancun */
  private static final int DEFAULT_TARGET_BLOBS_PER_BLOCK_CANCUN = 3;

  /** this.getBlobGasPerBlob() * 3 blobs = 131072 * 6 = 393216 = 0x60000 */
  private final long targetBlobGasPerBlock;

  /** Instantiates a new Cancun Gas Calculator. */
  public CancunGasCalculator() {
    this(KZG_POINT_EVAL.toArrayUnsafe()[19], DEFAULT_TARGET_BLOBS_PER_BLOCK_CANCUN);
  }

  /**
   * Instantiates a new Cancun Gas Calculator
   *
   * @param targetBlobsPerBlock the target blobs per block
   */
  public CancunGasCalculator(final int targetBlobsPerBlock) {
    this(KZG_POINT_EVAL.toArrayUnsafe()[19], targetBlobsPerBlock);
  }

  /**
   * Instantiates a new Cancun Gas Calculator
   *
   * @param maxPrecompile the max precompile
   * @param targetBlobsPerBlock the target blobs per block
   */
  protected CancunGasCalculator(final int maxPrecompile, final int targetBlobsPerBlock) {
    super(maxPrecompile);
    this.targetBlobGasPerBlock = getBlobGasPerBlob() * targetBlobsPerBlock;
  }

  private static final long TLOAD_GAS = WARM_STORAGE_READ_COST;
  private static final long TSTORE_GAS = WARM_STORAGE_READ_COST;

  /**
   * The blob gas cost per blob. This is the gas cost for each blob of data that is added to the
   * block.
   */
  private static final long BLOB_GAS_PER_BLOB = 1 << 17;

  // EIP-1153
  @Override
  public long getTransientLoadOperationGasCost() {
    return TLOAD_GAS;
  }

  @Override
  public long getTransientStoreOperationGasCost() {
    return TSTORE_GAS;
  }

  @Override
  public long blobGasCost(final long blobCount) {
    return getBlobGasPerBlob() * blobCount;
  }

  @Override
  public long getBlobGasPerBlob() {
    return BLOB_GAS_PER_BLOB;
  }

  /**
   * Retrieves the target blob gas per block.
   *
   * @return The target blob gas per block.
   */
  public long getTargetBlobGasPerBlock() {
    return targetBlobGasPerBlock;
  }

  @Override
  public long computeExcessBlobGas(final long parentExcessBlobGas, final long parentBlobGasUsed) {
    final long currentExcessBlobGas = parentExcessBlobGas + parentBlobGasUsed;
    if (currentExcessBlobGas < targetBlobGasPerBlock) {
      return 0L;
    }
    return currentExcessBlobGas - targetBlobGasPerBlock;
  }
}
