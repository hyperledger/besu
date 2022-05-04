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
 *
 */
package org.hyperledger.besu.evm.gascalculator;

import org.hyperledger.besu.evm.precompile.BigIntegerModularExponentiationPrecompiledContract;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;

public class ByzantiumGasCalculator extends SpuriousDragonGasCalculator {
  private static final BigInteger GQUADDIVISOR = BigInteger.valueOf(20);
  private static final BigInteger WORD_SIZE = BigInteger.valueOf(32);
  private static final BigInteger BITS_IN_BYTE = BigInteger.valueOf(8);

  public static final BigInteger MAX_FIRST_EXPONENT_BYTES = BigInteger.valueOf(32);
  public static final int MAX_GAS_BITS = 63;

  @Override
  public long modExpGasCost(final Bytes input) {
    final BigInteger baseLength =
        BigIntegerModularExponentiationPrecompiledContract.baseLength(input);
    final BigInteger exponentLength =
        BigIntegerModularExponentiationPrecompiledContract.exponentLength(input);
    final BigInteger modulusLength =
        BigIntegerModularExponentiationPrecompiledContract.modulusLength(input);
    final BigInteger exponentOffset =
        BigIntegerModularExponentiationPrecompiledContract.BASE_OFFSET.add(baseLength);
    final int firstExponentBytesCap = exponentLength.min(MAX_FIRST_EXPONENT_BYTES).intValue();
    final BigInteger firstExpBytes =
        BigIntegerModularExponentiationPrecompiledContract.extractParameter(
            input, exponentOffset, firstExponentBytesCap);
    final BigInteger adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);
    final BigInteger multiplicationComplexity =
        BigIntegerModularExponentiationPrecompiledContract.multiplicationComplexity(
            baseLength.max(modulusLength));
    final BigInteger gasRequirement =
        multiplicationComplexity
            .multiply(adjustedExponentLength.max(BigInteger.ONE))
            .divide(GQUADDIVISOR);

    // Gas price is so large it will not fit in a Gas type, so a
    // very very very unlikely high gas price is used instead.
    if (gasRequirement.bitLength() > MAX_GAS_BITS) {
      return Long.MAX_VALUE;
    } else {
      return gasRequirement.longValueExact();
    }
  }

  public static BigInteger adjustedExponentLength(
      final BigInteger exponentLength, final BigInteger firstExpBytes) {
    final BigInteger bitLength = bitLength(firstExpBytes);
    if (exponentLength.compareTo(WORD_SIZE) <= 0) {
      return bitLength;
    } else {
      return BITS_IN_BYTE.multiply(exponentLength.subtract(WORD_SIZE)).add(bitLength);
    }
  }

  private static BigInteger bitLength(final BigInteger n) {
    return n.compareTo(BigInteger.ZERO) == 0
        ? BigInteger.ZERO
        : BigInteger.valueOf(n.bitLength() - 1L);
  }
}
