/*
 * Copyright ConsenSys AG.
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

import static org.hyperledger.besu.evm.internal.Words.clampedAdd;
import static org.hyperledger.besu.evm.internal.Words.clampedMultiply;
import static org.hyperledger.besu.evm.internal.Words.clampedToInt;

import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

/** The Byzantium gas calculator. */
public class ByzantiumGasCalculator extends SpuriousDragonGasCalculator {
  private static final int GQUADDIVISOR = 20;
  private static final int WORD_SIZE = 32;
  private static final int BITS_IN_BYTE = 8;

  /** The constant MAX_FIRST_EXPONENT_BYTES. */
  public static final int MAX_FIRST_EXPONENT_BYTES = 32;

  /** Default constructor. */
  public ByzantiumGasCalculator() {}

  @Override
  public long modExpGasCost(final Bytes input) {
    final long baseLength = BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final long exponentLength =
        BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final long modulusLength =
        BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final long exponentOffset =
        clampedAdd(BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET, baseLength);
    final long firstExponentBytesCap = Math.min(exponentLength, MAX_FIRST_EXPONENT_BYTES);
    final BigInteger firstExpBytes =
        BigIntegerModularExponentiationPrecompiledContract.extractParameter(
            input, clampedToInt(exponentOffset), clampedToInt(firstExponentBytesCap));
    final long adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);
    final long multiplicationComplexity =
        BigIntegerModularExponentiationPrecompiledContract.multiplicationComplexity(
            Math.max(baseLength, modulusLength));
    long numerator = clampedMultiply(multiplicationComplexity, Math.max(adjustedExponentLength, 1));
    return (numerator == Long.MAX_VALUE) ? Long.MAX_VALUE : numerator / GQUADDIVISOR;
  }

  /**
   * Adjusted exponent length.
   *
   * @param exponentLength the exponent length
   * @param firstExpBytes the first exp bytes
   * @return the long
   */
  public static long adjustedExponentLength(
      final long exponentLength, final BigInteger firstExpBytes) {
    final int bitLength = bitLength(firstExpBytes);
    if (exponentLength < WORD_SIZE) {
      return bitLength;
    } else {
      return clampedAdd(clampedMultiply(BITS_IN_BYTE, (exponentLength - WORD_SIZE)), bitLength);
    }
  }

  private static int bitLength(final BigInteger n) {
    return n.compareTo(BigInteger.ZERO) == 0 ? 0 : (n.bitLength() - 1);
  }
}
