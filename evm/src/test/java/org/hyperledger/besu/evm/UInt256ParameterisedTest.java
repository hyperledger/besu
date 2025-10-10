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
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
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

  /** Provides unary test cases with signed interpretation (for SMod tests). */
  static Stream<BigInteger> provideSignedUnaryTestCases() {
    List<BigInteger> cases = new ArrayList<>();

    // Basic values
    cases.add(BigInteger.ZERO);
    cases.add(BigInteger.ONE);
    cases.add(BigInteger.TWO);
    cases.add(BigInteger.valueOf(3));

    // Boundary values
    cases.add(BigInteger.valueOf(Short.MAX_VALUE));
    cases.add(BigInteger.valueOf(0xFFFF)); // UnsignedShort.MAX_VALUE
    cases.add(BigInteger.valueOf(Integer.MAX_VALUE));
    cases.add(BigInteger.valueOf(0xFFFFFFFFL)); // UnsignedInteger.MAX_VALUE
    cases.add(BigInteger.valueOf(Long.MAX_VALUE));

    // Critical test cases: Values with MSB of top limb set, but positive in 256-bit space
    // These expose bugs in isNegative() when stored in fewer than 8 limbs
    cases.add(new BigInteger("80000000", 16)); // 32-bit: bit 31 set, but bit 255 clear
    cases.add(new BigInteger("80000001", 16)); // 32-bit: bit 31 set, but bit 255 clear
    cases.add(new BigInteger("8000000000000000", 16)); // 64-bit: bit 63 set, but bit 255 clear
    cases.add(new BigInteger("8000000000000001", 16)); // 64-bit: bit 63 set, but bit 255 clear

    // Large values - unsigned interpretation
    cases.add(new BigInteger("FFFFFFFFFFFFFFFF", 16)); // UnsignedLong.MAX_VALUE
    cases.add(new BigInteger("080000000000000008000000000000001", 16));
    cases.add(
        new BigInteger("fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe", 16));
    cases.add(
        new BigInteger("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16));
    cases.add(TWO_TO_64);
    cases.add(TWO_TO_128);
    cases.add(TWO_TO_192);
    cases.add(TWO_TO_128.subtract(BigInteger.ONE)); // UInt128Max
    cases.add(TWO_TO_192.subtract(BigInteger.ONE)); // UInt192Max

    // Int256 boundaries
    BigInteger INT256_MAX = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE); // 2^255 - 1
    BigInteger INT256_MIN = INT256_MAX.negate(); // -(2^255 - 1)
    cases.add(INT256_MAX);
    cases.add(INT256_MIN);

    cases.add(UINT256_MAX);

    // Add random signed values
    cases.addAll(generateRandomSigned(RANDOM_TEST_COUNT));

    return cases.stream();
  }

  /** Provides signed binary test cases (pairs of BigInteger values for signed operations). */
  static Stream<Arguments> provideSignedBinaryTestCases() {
    List<BigInteger> unary = provideSignedUnaryTestCases().toList();
    List<Arguments> binary = new ArrayList<>();

    for (BigInteger a : unary) {
      for (BigInteger b : unary) {
        binary.add(Arguments.of(a, b));
      }
    }

    return binary.stream();
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

  /** Generates random signed 256-bit BigInteger values (can be positive or negative). */
  private static List<BigInteger> generateRandomSigned(final int count) {
    List<BigInteger> randoms = new ArrayList<>();
    Random rand = new Random(12345); // Same seed as unsigned for consistency
    byte[] data = new byte[32]; // 256 bits = 32 bytes

    for (int i = 0; i < count; i++) {
      rand.nextBytes(data);
      // Don't clear sign bit - allow negative values
      randoms.add(new BigInteger(data));
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
  @MethodSource("provideSignedBinaryTestCases")
  void testSMod(final BigInteger a, final BigInteger b) {
    if (b.equals(BigInteger.ZERO)) {
      return; // Skip division by zero
    }

    Bytes32 expected;
    BigInteger rem;
    if (BigInteger.ZERO.compareTo(b) == 0) expected = Bytes32.ZERO;
    else {
      rem = a.abs().mod(b.abs());
      if ((a.compareTo(BigInteger.ZERO) < 0) && (rem.compareTo(BigInteger.ZERO) != 0)) {
        rem = rem.negate();
        expected = bigIntTo32B(rem, -1);
      } else {
        expected = bigIntTo32B(rem, 1);
      }
    }

    UInt256 uint256a = UInt256.fromSignedBytesBE(a.toByteArray());
    UInt256 uint256b = UInt256.fromSignedBytesBE(b.toByteArray());
    Bytes32 result = Bytes32.leftPad(Bytes.wrap(uint256a.signedMod(uint256b).toBytesBE()));

    assertThat(result).as("testSMod(%s, %s)", a, b).isEqualTo(expected);
  }

  private Bytes32 bigIntTo32B(final BigInteger x, final int sign) {
    if (sign >= 0) return bigIntTo32B(x);
    byte[] a = new byte[32];
    Arrays.fill(a, (byte) 0xFF);
    byte[] b = x.toByteArray();
    System.arraycopy(b, 0, a, 32 - b.length, b.length);
    if (a.length > 32) return Bytes32.wrap(a, a.length - 32);
    return Bytes32.leftPad(Bytes.wrap(a));
  }

  private Bytes32 bigIntTo32B(final BigInteger x) {
    byte[] a = x.toByteArray();
    if (a.length > 32) return Bytes32.wrap(a, a.length - 32);
    return Bytes32.leftPad(Bytes.wrap(a));
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
