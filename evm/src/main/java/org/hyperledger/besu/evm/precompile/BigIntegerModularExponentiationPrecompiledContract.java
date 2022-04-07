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
package org.hyperledger.besu.evm.precompile;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import java.math.BigInteger;
import java.util.Arrays;
import javax.annotation.Nonnull;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

// The big integer modular exponentiation precompiled contract defined in EIP-198.
public class BigIntegerModularExponentiationPrecompiledContract
    extends AbstractPrecompiledContract {

  public static final BigInteger BASE_OFFSET = BigInteger.valueOf(96);
  private static final int PARAMETER_LENGTH = 32;
  private static final int BASE_LENGTH_OFFSET = 0;
  private static final int EXPONENT_LENGTH_OFFSET = 32;
  private static final int MODULUS_LENGTH_OFFSET = 64;

  private static final BigInteger BIGINT_4 = BigInteger.valueOf(4);
  private static final BigInteger BIGINT_16 = BigInteger.valueOf(16);
  private static final BigInteger BIGINT_64 = BigInteger.valueOf(64);
  private static final BigInteger BIGINT_96 = BigInteger.valueOf(96);
  private static final BigInteger BIGINT_480 = BigInteger.valueOf(480);
  private static final BigInteger BIGINT_1024 = BigInteger.valueOf(1_024L);
  private static final BigInteger BIGINT_3072 = BigInteger.valueOf(3_072L);
  private static final BigInteger BIGINT_199680 = BigInteger.valueOf(199_680L);

  public BigIntegerModularExponentiationPrecompiledContract(final GasCalculator gasCalculator) {
    super("BigIntModExp", gasCalculator);
  }

  @Override
  public long gasRequirement(final Bytes input) {
    return gasCalculator().modExpGasCost(input);
  }

  @Nonnull
  @Override
  public PrecompileContractResult computePrecompile(
      final Bytes input, @Nonnull final MessageFrame messageFrame) {
    final BigInteger baseLength = baseLength(input);
    final BigInteger exponentLength = exponentLength(input);
    final BigInteger modulusLength = modulusLength(input);
    // If baseLength and modulusLength are zero
    // we could have a massively overflowing exp because it wouldn't have been filtered out at the
    // gas cost phase
    if (baseLength.equals(BigInteger.ZERO) && modulusLength.equals(BigInteger.ZERO)) {
      return PrecompileContractResult.success(Bytes.EMPTY);
    }
    final BigInteger exponentOffset = BASE_OFFSET.add(baseLength);
    final BigInteger modulusOffset = exponentOffset.add(exponentLength);
    final BigInteger base = extractParameter(input, BASE_OFFSET, baseLength.intValue());
    final BigInteger exp = extractParameter(input, exponentOffset, exponentLength.intValue());
    final BigInteger mod = extractParameter(input, modulusOffset, modulusLength.intValue());

    final Bytes modExp;
    // Result must be the length of the modulus.
    final MutableBytes result = MutableBytes.create(modulusLength.intValue());
    if (mod.compareTo(BigInteger.ZERO) == 0) {
      modExp = MutableBytes.EMPTY;
    } else {
      // BigInteger zero-pads positive values whose most significant bit is a 1 if
      // the padding was not there.
      modExp = Bytes.wrap(base.modPow(exp, mod).toByteArray()).trimLeadingZeros();
    }

    modExp.copyTo(result, result.size() - modExp.size());
    return PrecompileContractResult.success(result);
  }

  // Equation to estimate the multiplication complexity.
  public static BigInteger multiplicationComplexity(final BigInteger x) {
    if (x.compareTo(BIGINT_64) <= 0) {
      return square(x);
    } else if (x.compareTo(BIGINT_1024) <= 0) {
      return square(x).divide(BIGINT_4).add(BIGINT_96.multiply(x)).subtract(BIGINT_3072);
    } else {
      return square(x).divide(BIGINT_16).add(BIGINT_480.multiply(x)).subtract(BIGINT_199680);
    }
  }

  public static BigInteger baseLength(final Bytes input) {
    return extractParameter(input, BASE_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  public static BigInteger exponentLength(final Bytes input) {
    return extractParameter(input, EXPONENT_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  public static BigInteger modulusLength(final Bytes input) {
    return extractParameter(input, MODULUS_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  private static BigInteger extractParameter(
      final Bytes input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.toArray(), offset, offset + length);
    return new BigInteger(1, raw);
  }

  public static BigInteger extractParameter(
      final Bytes input, final BigInteger offset, final int length) {
    if (BigInteger.valueOf(input.size()).compareTo(offset) <= 0) {
      return BigInteger.ZERO;
    }
    return extractParameter(input, offset.intValue(), length);
  }

  private static BigInteger square(final BigInteger n) {
    return n.multiply(n);
  }
}
