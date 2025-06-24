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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import static org.hyperledger.besu.datatypes.Address.KZG_POINT_EVAL;
import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

/**
 * Gas Calculator for Cancun
 *
 * <UL>
 *   <LI>Gas costs for TSTORE/TLOAD
 *   <LI>Blob gas for EIP-4844
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
  protected CancunGasCalculator(final int maxPrecompile) {
    super(maxPrecompile);
  }

  private static final long TLOAD_GAS = WARM_STORAGE_READ_COST;
  private static final long TSTORE_GAS = WARM_STORAGE_READ_COST;

  /**
   * The blob gas cost per blob. This is the gas cost for each blob of data that is added to the
   * block. 1 << 17 = 131072 = 0x20000
   */
  private static final long BLOB_GAS_PER_BLOB = 1 << 17;


  // Only for performance testing purposes, this code should not be merged into main
  @Override
  public long modExpGasCost(final Bytes input) {
    final long baseLength = BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final long exponentLength =
            BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final long modulusLength =
            BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final long exponentOffset =
            clampedAdd(BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET, baseLength);

    final long maxLength = Math.max(modulusLength, baseLength);
    if (maxLength <= 0) {
      return 500L;
    }
    long multiplicationComplexity = 16;
    long words = (maxLength + 7L) / 8L;
    words = Words.clampedMultiply(words, words);
    if (maxLength > 32) {
      multiplicationComplexity = words * 2;
    }

    long maxExponentLength = Long.MAX_VALUE / words * 3 / 8;
    if (exponentLength > maxExponentLength) {
      return Long.MAX_VALUE;
    }

    final long firstExponentBytesCap =
            Math.min(exponentLength, ByzantiumGasCalculator.MAX_FIRST_EXPONENT_BYTES);
    final BigInteger firstExpBytes =
            BigIntegerModularExponentiationPrecompiledContract.extractParameter(
                    input, clampedToInt(exponentOffset), clampedToInt(firstExponentBytesCap));
    final long adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);

    long gasRequirement =
            clampedMultiply(multiplicationComplexity, Math.max(adjustedExponentLength, 1L));
    if (gasRequirement != Long.MAX_VALUE) {
      gasRequirement /= 3;
    }

    return Math.max(gasRequirement, 500L);
  }


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
}
