/*
 * Copyright Hyperledger Besu contributors.
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
 *   <LI>Data gas for EIP-4844
 * </UL>
 */
public class CancunGasCalculator extends ShanghaiGasCalculator {

  /** Instantiates a new Cancun Gas Calculator. */
  public CancunGasCalculator() {
    this(KZG_POINT_EVAL.toArrayUnsafe()[19]);
  }

  /**
   * Instantiates a new Cancun Gas Calculator
   *
   * @param maxPrecompile the max precompile
   */
  private CancunGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  private static final long TLOAD_GAS = WARM_STORAGE_READ_COST;
  private static final long TSTORE_GAS = WARM_STORAGE_READ_COST;

  /**
   * The data gas cost per blob. This is the gas cost for each blob of data that is added to the
   * block.
   */
  public static final long DATA_GAS_PER_BLOB = 1 << 17;

  /** The target data gas per block. */
  public static final long TARGET_DATA_GAS_PER_BLOCK = 0x60000;

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
  public long dataGasCost(final int blobCount) {
    return DATA_GAS_PER_BLOB * blobCount;
  }

  /**
   * Retrieves the target data gas per block.
   *
   * @return The target data gas per block.
   */
  public long getTargetDataGasPerBlock() {
    return TARGET_DATA_GAS_PER_BLOCK;
  }

  /**
   * Computes the excess data gas for a given block based on the parent's excess data gas and data
   * gas used. If the sum of parent's excess data gas and parent's data gas used is less than the
   * target data gas per block, the excess data gas is calculated as 0. Otherwise, it is computed as
   * the difference between the sum and the target data gas per block.
   *
   * @param parentExcessDataGas The excess data gas of the parent block.
   * @param newBlobs data gas incurred by current block
   * @return The excess data gas for the current block.
   */
  @Override
  public long computeExcessDataGas(final long parentExcessDataGas, final int newBlobs) {
    final long consumedDataGas = dataGasCost(newBlobs);
    final long currentExcessDataGas = parentExcessDataGas + consumedDataGas;

    if (currentExcessDataGas < TARGET_DATA_GAS_PER_BLOCK) {
      return 0L;
    }
    return currentExcessDataGas - TARGET_DATA_GAS_PER_BLOCK;
  }

  @Override
  public long computeExcessDataGas(final long parentExcessDataGas, final long dataGasUsed) {
    final long currentExcessDataGas = parentExcessDataGas + dataGasUsed;

    if (currentExcessDataGas < TARGET_DATA_GAS_PER_BLOCK) {
      return 0L;
    }
    return currentExcessDataGas - TARGET_DATA_GAS_PER_BLOCK;
  }
}
