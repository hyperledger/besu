/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.mainnet.precompiles;

import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.mainnet.AbstractPrecompiledContract;
import org.hyperledger.besu.ethereum.vm.GasCalculator;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;
import org.hyperledger.besu.util.bytes.MutableBytesValue;

import java.math.BigInteger;
import java.util.Arrays;

// The big integer modular exponentiation precompiled contract defined in EIP-198.
public class BigIntegerModularExponentiationPrecompiledContract
    extends AbstractPrecompiledContract {

  private static final BigInteger WORD_SIZE = BigInteger.valueOf(32);
  private static final BigInteger BITS_IN_BYTE = BigInteger.valueOf(8);
  private static final BigInteger BASE_OFFSET = BigInteger.valueOf(96);
  private static final BigInteger MAX_FIRST_EXPONENT_BYTES = BigInteger.valueOf(32);
  private static final BigInteger GQUADDIVISOR = BigInteger.valueOf(20);
  private static final int PARAMETER_LENGTH = 32;
  private static final int BASE_LENGTH_OFFSET = 0;
  private static final int EXPONENT_LENGTH_OFFSET = 32;
  private static final int MODULUS_LENGTH_OFFSET = 64;
  private static final int MAX_GAS_BITS = 255;

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
  public Gas gasRequirement(final BytesValue input) {
    // Typically gas calculations are delegated to a GasCalculator instance,
    // but the complexity and coupling wih other parts of the precompile seem
    // like reasonable reasons to do the math here instead.
    final BigInteger baseLength = baseLength(input);
    final BigInteger exponentLength = exponentLength(input);
    final BigInteger modulusLength = modulusLength(input);
    final BigInteger exponentOffset = BASE_OFFSET.add(baseLength);
    final int firstExponentBytesCap = exponentLength.min(MAX_FIRST_EXPONENT_BYTES).intValue();
    final BigInteger firstExpBytes = extractParameter(input, exponentOffset, firstExponentBytesCap);
    final BigInteger adjustedExponentLength = adjustedExponentLength(exponentLength, firstExpBytes);
    final BigInteger multiplicationComplexity =
        multiplicationComplexity(baseLength.max(modulusLength));
    final BigInteger gasRequirement =
        multiplicationComplexity
            .multiply(adjustedExponentLength.max(BigInteger.ONE))
            .divide(GQUADDIVISOR);

    // Gas price is so large it will not fit in a Gas type, so an
    // very very very unlikely high gas price is used instead.
    if (gasRequirement.bitLength() > MAX_GAS_BITS) {
      return Gas.of(Long.MAX_VALUE);
    } else {
      return Gas.of(gasRequirement);
    }
  }

  @Override
  public BytesValue compute(final BytesValue input, final MessageFrame messageFrame) {
    final BigInteger baseLength = baseLength(input);
    final BigInteger exponentLength = exponentLength(input);
    final BigInteger modulusLength = modulusLength(input);
    final BigInteger exponentOffset = BASE_OFFSET.add(baseLength);
    final BigInteger modulusOffset = exponentOffset.add(exponentLength);
    final BigInteger base = extractParameter(input, BASE_OFFSET, baseLength.intValue());
    final BigInteger exp = extractParameter(input, exponentOffset, exponentLength.intValue());
    final BigInteger mod = extractParameter(input, modulusOffset, modulusLength.intValue());

    final BytesValue modExp;
    // Result must be the length of the modulus.
    final MutableBytesValue result = MutableBytesValue.create(modulusLength.intValue());
    if (mod.compareTo(BigInteger.ZERO) == 0) {
      modExp = MutableBytesValue.EMPTY;
    } else {
      // BigInteger zero-pads positive values whose most significant bit is a 1 if
      // the padding was not there.
      modExp =
          BytesValues.trimLeadingZeros(MutableBytesValue.wrap(base.modPow(exp, mod).toByteArray()));
    }

    modExp.copyTo(result, result.size() - modExp.size());
    return result;
  }

  // Equation to estimate the multiplication complexity.
  private static BigInteger multiplicationComplexity(final BigInteger x) {
    if (x.compareTo(BIGINT_64) <= 0) {
      return square(x);
    } else if (x.compareTo(BIGINT_1024) <= 0) {
      return square(x).divide(BIGINT_4).add(BIGINT_96.multiply(x)).subtract(BIGINT_3072);
    } else {
      return square(x).divide(BIGINT_16).add(BIGINT_480.multiply(x)).subtract(BIGINT_199680);
    }
  }

  private static BigInteger bitLength(final BigInteger n) {
    return n.compareTo(BigInteger.ZERO) == 0
        ? BigInteger.ZERO
        : BigInteger.valueOf(n.bitLength() - 1);
  }

  private static BigInteger adjustedExponentLength(
      final BigInteger exponentLength, final BigInteger firstExpBytes) {
    final BigInteger bitLength = bitLength(firstExpBytes);
    if (exponentLength.compareTo(WORD_SIZE) <= 0) {
      return bitLength;
    } else {
      return BITS_IN_BYTE.multiply(exponentLength.subtract(WORD_SIZE)).add(bitLength);
    }
  }

  private static final BigInteger baseLength(final BytesValue input) {
    return extractParameter(input, BASE_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  private static final BigInteger exponentLength(final BytesValue input) {
    return extractParameter(input, EXPONENT_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  private static final BigInteger modulusLength(final BytesValue input) {
    return extractParameter(input, MODULUS_LENGTH_OFFSET, PARAMETER_LENGTH);
  }

  private static BigInteger extractParameter(
      final BytesValue input, final int offset, final int length) {
    if (offset > input.size() || length == 0) {
      return BigInteger.ZERO;
    }
    final byte[] raw = Arrays.copyOfRange(input.extractArray(), offset, offset + length);
    return new BigInteger(1, raw);
  }

  private static BigInteger extractParameter(
      final BytesValue input, final BigInteger offset, final int length) {
    if (BigInteger.valueOf(input.size()).compareTo(offset) <= 0) {
      return BigInteger.ZERO;
    }
    return extractParameter(input, offset.intValue(), length);
  }

  private static BigInteger square(final BigInteger n) {
    return n.multiply(n);
  }
}
