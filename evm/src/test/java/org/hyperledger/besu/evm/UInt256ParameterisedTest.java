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
package org.hyperledger.besu.evm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UInt256ParameterisedTest {

  // Test constants
  private static final BigInteger TWO_TO_64 = BigInteger.TWO.pow(64);
  private static final BigInteger TWO_TO_128 = BigInteger.TWO.pow(128);
  private static final BigInteger TWO_TO_192 = BigInteger.TWO.pow(192);
  private static final BigInteger TWO_TO_256 = BigInteger.TWO.pow(256);
  private static final BigInteger UINT256_MAX = TWO_TO_256.subtract(BigInteger.ONE);

  private static final int RANDOM_TEST_COUNT = 6;

  // region Test Data Providers

  /** Provides unary test cases (single BigInteger values). */
  static Stream<BigInteger> provideUnaryTestCases() {
    List<BigInteger> cases = new ArrayList<>();

    // Basic values
    cases.add(BigInteger.ZERO);
    cases.add(BigInteger.ONE);
    cases.add(BigInteger.TWO);
    cases.add(BigInteger.valueOf(3));

    // Boundary values
    cases.add(BigInteger.valueOf(Short.MAX_VALUE));
    cases.add(BigInteger.valueOf(0xFFFF - 1)); // UnsignedShort.MAX_VALUE - 1
    cases.add(BigInteger.valueOf(0xFFFF)); // UnsignedShort.MAX_VALUE
    cases.add(BigInteger.valueOf(0xFFFF + 1)); // UnsignedShort.MAX_VALUE + 1
    cases.add(BigInteger.valueOf(Integer.MAX_VALUE));
    cases.add(BigInteger.valueOf(0xFFFFFFFFL - 1)); // UnsignedInteger.MAX_VALUE - 1
    cases.add(BigInteger.valueOf(0xFFFFFFFFL)); // UnsignedInteger.MAX_VALUE
    cases.add(BigInteger.valueOf(0xFFFFFFFFL + 1)); // UnsignedInteger.MAX_VALUE + 1
    cases.add(BigInteger.valueOf(Long.MAX_VALUE));

    // Large values
    cases.add(new BigInteger("FFFFFFFFFFFFFFFE", 16)); // UnsignedLong.MAX_VALUE - 1
    cases.add(new BigInteger("FFFFFFFFFFFFFFFF", 16)); // UnsignedLong.MAX_VALUE
    cases.add(new BigInteger("080000000000000008000000000000001", 16));
    cases.add(TWO_TO_64);
    cases.add(TWO_TO_128);
    cases.add(TWO_TO_192);
    cases.add(TWO_TO_128.subtract(BigInteger.ONE)); // UInt128Max
    cases.add(TWO_TO_192.subtract(BigInteger.ONE)); // UInt192Max
    cases.add(UINT256_MAX);

    // Add random values
    cases.addAll(generateRandomUnsigned(RANDOM_TEST_COUNT));

    return cases.stream();
  }

  /** Provides binary test cases (pairs of BigInteger values). */
  static Stream<Arguments> provideBinaryTestCases() {
    List<BigInteger> unary = provideUnaryTestCases().toList();
    List<Arguments> binary = new ArrayList<>();

    for (BigInteger a : unary) {
      for (BigInteger b : unary) {
        binary.add(Arguments.of(a, b));
      }
    }

    return binary.stream();
  }

  /** Provides ternary test cases (triples of BigInteger values). */
  static Stream<Arguments> provideTernaryTestCases() {
    List<Arguments> binary = provideBinaryTestCases().toList();
    List<BigInteger> unary = provideUnaryTestCases().toList();
    List<Arguments> ternary = new ArrayList<>();

    for (Arguments binArgs : binary) {
      BigInteger a = (BigInteger) binArgs.get()[0];
      BigInteger b = (BigInteger) binArgs.get()[1];
      for (BigInteger c : unary) {
        ternary.add(Arguments.of(a, b, c));
      }
    }

    return ternary.stream();
  }

  /** Provides shift test cases (BigInteger value and int shift amount). */
  static Stream<Arguments> provideShiftTestCases() {
    List<BigInteger> unary = provideUnaryTestCases().toList();
    List<Arguments> shifts = new ArrayList<>();

    for (BigInteger value : unary) {
      for (int shift = 0; shift <= 256; shift++) {
        shifts.add(Arguments.of(value, shift));
      }
    }

    return shifts.stream();
  }

  // endregion

  // region Helper Methods

  /** Converts BigInteger to UInt256, wrapping to 256-bit range. */
  private static UInt256 toUInt256(final BigInteger value) {
    BigInteger wrapped = value.mod(TWO_TO_256);
    return fromBigInteger(wrapped);
  }

  /**
   * Create UInt256 from BigInteger.
   *
   * @param value BigInteger value to convert (must be non-negative and <= 2^256-1)
   * @return UInt256 representation of the BigInteger value.
   * @throws IllegalArgumentException if value is negative or exceeds 256 bits
   */
  private static UInt256 fromBigInteger(final java.math.BigInteger value) {
    if (value.signum() < 0) {
      throw new IllegalArgumentException("UInt256 cannot represent negative values");
    }
    if (value.bitLength() > 256) {
      throw new IllegalArgumentException("Value exceeds 256 bits");
    }
    if (value.equals(java.math.BigInteger.ZERO)) return UInt256.ZERO;

    byte[] bytes = value.toByteArray();
    // Remove sign byte if present
    int offset = 0;
    if (bytes.length > 32 || (bytes.length > 0 && bytes[0] == 0)) {
      offset = bytes.length - 32;
      if (offset < 0) {
        // Need to pad with zeros
        byte[] padded = new byte[32];
        System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
        return UInt256.fromBytesBE(padded);
      }
    }

    return UInt256.fromBytesBE(java.util.Arrays.copyOfRange(bytes, offset, bytes.length));
  }

  private static BigInteger toBigInteger(final UInt256 value) {
    if (value.isZero()) return java.math.BigInteger.ZERO;
    byte[] bytes = value.toBytesBE();
    return new java.math.BigInteger(1, bytes);
  }

  /** Generates random unsigned 256-bit BigInteger values. */
  private static List<BigInteger> generateRandomUnsigned(final int count) {
    List<BigInteger> randoms = new ArrayList<>();
    Random rand = new Random(12345);
    byte[] data = new byte[32];

    for (int i = 0; i < count; i++) {
      rand.nextBytes(data);
      data[data.length - 1] &= 0x7F; // Clear sign bit to ensure positive
      randoms.add(new BigInteger(1, data));
    }

    return randoms;
  }

  /** Wraps result to 256-bit range. */
  private static BigInteger wrap256(final BigInteger value) {
    return value.mod(TWO_TO_256);
  }

  // endregion

  // region Arithmetic Operations Tests

  @ParameterizedTest
  @MethodSource("provideBinaryTestCases")
  void testAdd(final BigInteger a, final BigInteger b) {
    BigInteger expected = wrap256(a.add(b));

    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);
    UInt256 result = uint256a.add(uint256b);

    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideBinaryTestCases")
  void testMul(final BigInteger a, final BigInteger b) {
    BigInteger expected = wrap256(a.multiply(b));

    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);
    UInt256 result = uint256a.mul(uint256b);

    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideBinaryTestCases")
  void testMod(final BigInteger a, final BigInteger b) {
    if (b.equals(BigInteger.ZERO)) {
      return; // Skip division by zero
    }

    BigInteger expected = wrap256(a.mod(b));

    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);
    UInt256 result = uint256a.mod(uint256b);

    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideTernaryTestCases")
  void testAddMod(final BigInteger a, final BigInteger b, final BigInteger m) {
    if (m.equals(BigInteger.ZERO)) {
      return; // Skip division by zero
    }

    BigInteger expected = a.add(b).mod(m);
    expected = wrap256(expected);

    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);
    UInt256 uint256m = toUInt256(m);

    UInt256 result = uint256a.addMod(uint256b, uint256m);
    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideTernaryTestCases")
  void testMulMod(final BigInteger a, final BigInteger b, final BigInteger m) {
    if (m.equals(BigInteger.ZERO)) {
      return; // Skip division by zero
    }

    BigInteger expected = a.multiply(b).mod(m);
    expected = wrap256(expected);

    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);
    UInt256 uint256m = toUInt256(m);

    UInt256 result = uint256a.mulMod(uint256b, uint256m);
    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  // endregion

  // region Bitwise Operations Tests

  @ParameterizedTest
  @MethodSource("provideShiftTestCases")
  void testLeftShift(final BigInteger value, final int shift) {
    BigInteger expected = wrap256(value.shiftLeft(shift));

    UInt256 uint256 = toUInt256(value);
    UInt256 result = uint256.shiftLeft(shift);

    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("provideShiftTestCases")
  void testRightShift(final BigInteger value, final int shift) {
    BigInteger expected = wrap256(value.shiftRight(shift));

    UInt256 uint256 = toUInt256(value);
    UInt256 result = uint256.shiftRight(shift);

    assertThat(toBigInteger(result)).isEqualTo(expected);
  }

  // endregion

  // region Comparison Tests

  @ParameterizedTest
  @MethodSource("provideBinaryTestCases")
  void testComparisons(final BigInteger a, final BigInteger b) {
    UInt256 uint256a = toUInt256(a);
    UInt256 uint256b = toUInt256(b);

    assertThat(UInt256.compare(uint256a, uint256b) < 0).isEqualTo(a.compareTo(b) < 0);
    assertThat(UInt256.compare(uint256a, uint256b) <= 0).isEqualTo(a.compareTo(b) <= 0);
    assertThat(UInt256.compare(uint256a, uint256b) > 0).isEqualTo(a.compareTo(b) > 0);
    assertThat(UInt256.compare(uint256a, uint256b) >= 0).isEqualTo(a.compareTo(b) >= 0);
    assertThat(uint256a.equals(uint256b)).isEqualTo(a.equals(b));
  }

  // endregion

  // region Conversion Tests

  @ParameterizedTest
  @MethodSource("provideUnaryTestCases")
  void testToBigIntegerAndBack(final BigInteger value) {
    UInt256 uint256 = toUInt256(value);
    BigInteger result = toBigInteger(uint256);
    assertThat(result).isEqualTo(value);
  }

  // endregion
}
