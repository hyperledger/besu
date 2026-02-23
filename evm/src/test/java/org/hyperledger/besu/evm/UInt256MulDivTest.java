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

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive property-based and edge-case tests for UInt256 mul() and div() operations,
 * comparing against BigInteger to find correctness bugs.
 */
public class UInt256MulDivTest {

  private static final BigInteger TWO_256 = BigInteger.ONE.shiftLeft(256);
  private static final BigInteger MAX_UINT256 = TWO_256.subtract(BigInteger.ONE);

  // region Providers

  @Provide
  Arbitrary<byte[]> unsigned1to32() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(32)
        .map(UInt256MulDivTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> oneLimbDivisor() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(8)
        .map(UInt256MulDivTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> twoLimbDivisor() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(9)
        .ofMaxSize(16)
        .map(UInt256MulDivTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> threeLimbDivisor() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(17)
        .ofMaxSize(24)
        .map(UInt256MulDivTest::clampUnsigned32);
  }

  @Provide
  Arbitrary<byte[]> fourLimbDivisor() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(25)
        .ofMaxSize(32)
        .map(UInt256MulDivTest::clampUnsigned32);
  }

  // endregion

  // region 1. Property-based: mul() vs BigInteger

  @Property(tries = 10000)
  void property_mul_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.mul(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected = bigUnsignedToBytes32(A.multiply(B).mod(TWO_256));
    assertThat(got)
        .as("mul mismatch: %s * %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  @Property(tries = 10000)
  void property_mul_commutative(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);
    assertThat(ua.mul(ub)).isEqualTo(ub.mul(ua));
  }

  // endregion

  // region 2. Property-based: div() vs BigInteger

  @Property(tries = 10000)
  void property_div_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.div(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected;
    if (B.signum() == 0) {
      expected = Bytes32.ZERO.toArrayUnsafe();
    } else {
      expected = bigUnsignedToBytes32(A.divide(B));
    }
    assertThat(got)
        .as("div mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  // endregion

  // region 3. Property-based: div() with specific divisor sizes

  @Property(tries = 5000)
  void property_div_oneLimbDivisor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("oneLimbDivisor") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.div(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected =
        (B.signum() == 0) ? Bytes32.ZERO.toArrayUnsafe() : bigUnsignedToBytes32(A.divide(B));
    assertThat(got)
        .as("div1 mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  @Property(tries = 5000)
  void property_div_twoLimbDivisor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("twoLimbDivisor") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.div(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected = bigUnsignedToBytes32(A.divide(B));
    assertThat(got)
        .as("div2 mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  @Property(tries = 5000)
  void property_div_threeLimbDivisor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("threeLimbDivisor") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.div(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected = bigUnsignedToBytes32(A.divide(B));
    assertThat(got)
        .as("div3 mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  @Property(tries = 5000)
  void property_div_fourLimbDivisor(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("fourLimbDivisor") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    final byte[] got = ua.div(ub).toBytesBE();

    BigInteger A = toBigUnsigned(a);
    BigInteger B = toBigUnsigned(b);
    byte[] expected = bigUnsignedToBytes32(A.divide(B));
    assertThat(got)
        .as("div4 mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  // endregion

  // region 4. Property-based: signedDiv() vs BigInteger

  @Property(tries = 10000)
  void property_signedDiv_matchesBigInteger(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    final byte[] a32 = Bytes32.leftPad(Bytes.wrap(a)).toArrayUnsafe();
    final byte[] b32 = Bytes32.leftPad(Bytes.wrap(b)).toArrayUnsafe();
    final BigInteger A = new BigInteger(a32); // signed interpretation
    final BigInteger B = new BigInteger(b32); // signed interpretation
    final UInt256 ua = UInt256.fromBytesBE(a32);
    final UInt256 ub = UInt256.fromBytesBE(b32);

    final byte[] got = ua.signedDiv(ub).toBytesBE();

    byte[] expected;
    if (B.signum() == 0) {
      expected = Bytes32.ZERO.toArrayUnsafe();
    } else if (A.equals(minSigned256()) && B.equals(BigInteger.valueOf(-1))) {
      // Special case: MIN_INT256 / -1 = MIN_INT256 (overflow)
      expected = bigSignedToBytes32(A);
    } else {
      expected = bigSignedToBytes32(A.divide(B));
    }
    assertThat(got)
        .as("signedDiv mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  // endregion

  // region 5. Property-based: division invariant a == (a/b)*b + (a%b)

  @Property(tries = 10000)
  void property_div_mod_invariant(
      @ForAll("unsigned1to32") final byte[] a, @ForAll("unsigned1to32") final byte[] b) {
    final UInt256 ua = UInt256.fromBytesBE(a);
    final UInt256 ub = UInt256.fromBytesBE(b);

    if (ub.equals(UInt256.ZERO)) {
      return; // skip div by zero
    }

    UInt256 q = ua.div(ub);
    UInt256 r = ua.mod(ub);
    UInt256 reconstructed = q.mul(ub).add(r);

    assertThat(reconstructed)
        .as(
            "invariant a=(a/b)*b+(a%%b) failed: a=%s b=%s q=%s r=%s",
            ua.toHexString(), ub.toHexString(), q.toHexString(), r.toHexString())
        .isEqualTo(ua);
  }

  // endregion

  // region 6. Edge case tests for div()

  @Test
  void div_maxBy1() {
    assertDivMatchesBigInteger(MAX_UINT256, BigInteger.ONE);
  }

  @Test
  void div_maxBy2() {
    assertDivMatchesBigInteger(MAX_UINT256, BigInteger.TWO);
  }

  @Test
  void div_maxByMax() {
    assertDivMatchesBigInteger(MAX_UINT256, MAX_UINT256);
  }

  @Test
  void div_maxByMaxMinus1() {
    assertDivMatchesBigInteger(MAX_UINT256, MAX_UINT256.subtract(BigInteger.ONE));
  }

  @Test
  void div_pow128ByPow64() {
    assertDivMatchesBigInteger(BigInteger.ONE.shiftLeft(128), BigInteger.ONE.shiftLeft(64));
  }

  @Test
  void div_pow192ByPow128() {
    assertDivMatchesBigInteger(BigInteger.ONE.shiftLeft(192), BigInteger.ONE.shiftLeft(128));
  }

  @Test
  void div_pow255ByPow127() {
    assertDivMatchesBigInteger(BigInteger.ONE.shiftLeft(255), BigInteger.ONE.shiftLeft(127));
  }

  @Test
  void div_allOnesByAllOnesMinus1() {
    assertDivMatchesBigInteger(MAX_UINT256, MAX_UINT256.subtract(BigInteger.ONE));
  }

  @Test
  void div_allOnesByAllFsMinus1InLowerHalf() {
    // 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF / 0xFFFFFFFFFFFFFFFE
    BigInteger dividend = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    BigInteger divisor = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.TWO);
    assertDivMatchesBigInteger(dividend, divisor);
  }

  @Test
  void div_nearOverflowQuotientEstimate() {
    // Construct case where top 2 limbs of dividend / top limb of divisor ~ 2^64
    // dividend = (2^64 - 1) << 192 | (2^64 - 1) << 128
    // divisor  = (2^64 - 1) << 64 | 1
    BigInteger limb = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    BigInteger dividend = limb.shiftLeft(192).or(limb.shiftLeft(128));
    BigInteger divisor = limb.shiftLeft(64).or(BigInteger.ONE);
    assertDivMatchesBigInteger(dividend, divisor);
  }

  @Test
  void div_maxDividendSmallMultiLimbDivisor() {
    // MAX / (2^64 + 1)
    BigInteger divisor = BigInteger.ONE.shiftLeft(64).add(BigInteger.ONE);
    assertDivMatchesBigInteger(MAX_UINT256, divisor);
  }

  @Test
  void div_maxDividend3LimbDivisor() {
    // MAX / (2^128 + 1)
    BigInteger divisor = BigInteger.ONE.shiftLeft(128).add(BigInteger.ONE);
    assertDivMatchesBigInteger(MAX_UINT256, divisor);
  }

  @Test
  void div_addBackTriggerCase() {
    // Specifically constructed to trigger the add-back step in Knuth's Algorithm D.
    // When the trial quotient digit is too large by 1, the algorithm must subtract 1
    // and add back the divisor. This happens when:
    // - The normalized dividend's top 2 digits / top divisor digit gives a quotient
    //   that, when multiplied by the full divisor, exceeds the dividend portion.
    //
    // Using known add-back trigger: dividend close to divisor * (2^64 - 1)
    BigInteger divisor = new BigInteger("FFFFFFFFFFFFFFFF0000000000000001", 16);
    BigInteger qHigh = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE); // 2^64 - 1
    BigInteger dividend = divisor.multiply(qHigh).add(divisor.subtract(BigInteger.ONE));
    if (dividend.bitLength() <= 256) {
      assertDivMatchesBigInteger(dividend, divisor);
    }
  }

  @Test
  void div_addBackTriggerCase2() {
    // Another add-back case: divisor with high bit pattern that causes overestimate
    BigInteger divisor = new BigInteger("80000000000000000000000000000001", 16);
    BigInteger quotient = new BigInteger("FFFFFFFFFFFFFFFE", 16);
    BigInteger remainder = divisor.subtract(BigInteger.ONE);
    BigInteger dividend = divisor.multiply(quotient).add(remainder);
    if (dividend.bitLength() <= 256) {
      assertDivMatchesBigInteger(dividend, divisor);
    }
  }

  @Test
  void div_largeDividendSmall2LimbDivisor() {
    // A very large dividend divided by a small 2-limb divisor, producing large quotient
    BigInteger divisor = BigInteger.ONE.shiftLeft(64).add(BigInteger.valueOf(3));
    assertDivMatchesBigInteger(MAX_UINT256, divisor);
  }

  @Test
  void div_borrowOverflowCase() {
    // Case designed to trigger combined borrow overflow in divGeneral multiply-subtract loop
    // prodHi near 2^64-1 AND subtraction underflow simultaneously
    BigInteger divisor = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16); // 128-bit all-ones
    BigInteger dividend = MAX_UINT256; // 256-bit all-ones
    assertDivMatchesBigInteger(dividend, divisor);
  }

  @Test
  void div_borrowOverflowCase2() {
    // Divisor with top limb = 0xFFFFFFFFFFFFFFFF and dividend = MAX
    BigInteger divisor = new BigInteger("FFFFFFFFFFFFFFFF0000000000000000", 16);
    assertDivMatchesBigInteger(MAX_UINT256, divisor);
  }

  @Test
  void div_borrowOverflowCase3() {
    // 4-limb divisor with all-ones pattern
    BigInteger divisor =
        new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFE", 16);
    assertDivMatchesBigInteger(MAX_UINT256, divisor);
  }

  @Test
  void div_oneByOne() {
    assertDivMatchesBigInteger(BigInteger.ONE, BigInteger.ONE);
  }

  @Test
  void div_zeroByAny() {
    assertDivMatchesBigInteger(BigInteger.ZERO, BigInteger.valueOf(42));
  }

  @Test
  void div_byZero() {
    UInt256 result = UInt256.fromBytesBE(new byte[] {1}).div(UInt256.ZERO);
    assertThat(result).isEqualTo(UInt256.ZERO);
  }

  @Test
  void div_powersOf2() {
    for (int i = 1; i < 256; i++) {
      BigInteger dividend = BigInteger.ONE.shiftLeft(255);
      BigInteger divisor = BigInteger.ONE.shiftLeft(i);
      assertDivMatchesBigInteger(dividend, divisor);
    }
  }

  @Test
  void div_consecutiveValues() {
    // Test divisions of consecutive large values to stress boundary conditions
    for (int offset = 0; offset < 16; offset++) {
      BigInteger base = BigInteger.ONE.shiftLeft(128);
      BigInteger dividend = base.add(BigInteger.valueOf(offset));
      BigInteger divisor = BigInteger.ONE.shiftLeft(64).add(BigInteger.valueOf(offset));
      assertDivMatchesBigInteger(dividend, divisor);
    }
  }

  // endregion

  // region 7. Edge case tests for mul()

  @Test
  void mul_maxTimesMax() {
    assertMulMatchesBigInteger(MAX_UINT256, MAX_UINT256);
  }

  @Test
  void mul_maxTimes2() {
    assertMulMatchesBigInteger(MAX_UINT256, BigInteger.TWO);
  }

  @Test
  void mul_maxTimesMaxMinus1() {
    assertMulMatchesBigInteger(MAX_UINT256, MAX_UINT256.subtract(BigInteger.ONE));
  }

  @Test
  void mul_powersOf2() {
    for (int i = 0; i < 256; i++) {
      BigInteger a = BigInteger.ONE.shiftLeft(i);
      BigInteger b = BigInteger.ONE.shiftLeft(255 - i);
      assertMulMatchesBigInteger(a, b);
    }
  }

  @Test
  void mul_maxTimesOne() {
    assertMulMatchesBigInteger(MAX_UINT256, BigInteger.ONE);
  }

  @Test
  void mul_maxTimesZero() {
    assertMulMatchesBigInteger(MAX_UINT256, BigInteger.ZERO);
  }

  @Test
  void mul_halfMaxSquared() {
    BigInteger half = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);
    assertMulMatchesBigInteger(half, half);
  }

  @Test
  void mul_limbBoundaries() {
    // Values at limb boundaries: 2^64-1, 2^64, 2^128-1, etc.
    BigInteger[] boundaries = {
      BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE),
      BigInteger.ONE.shiftLeft(64),
      BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE),
      BigInteger.ONE.shiftLeft(128),
      BigInteger.ONE.shiftLeft(192).subtract(BigInteger.ONE),
      BigInteger.ONE.shiftLeft(192),
    };
    for (BigInteger a : boundaries) {
      for (BigInteger b : boundaries) {
        assertMulMatchesBigInteger(a, b);
      }
    }
  }

  @Test
  void mul_allOnesPattern() {
    // 0xFFFFFFFFFFFFFFFF * 0xFFFFFFFFFFFFFFFF (single limb max * single limb max)
    BigInteger limbMax = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    assertMulMatchesBigInteger(limbMax, limbMax);
  }

  // endregion

  // region Helpers

  private void assertDivMatchesBigInteger(final BigInteger a, final BigInteger b) {
    UInt256 ua = bigIntegerToUInt256(a);
    UInt256 ub = bigIntegerToUInt256(b);

    byte[] got = ua.div(ub).toBytesBE();

    byte[] expected;
    if (b.signum() == 0) {
      expected = Bytes32.ZERO.toArrayUnsafe();
    } else {
      expected = bigUnsignedToBytes32(a.divide(b));
    }
    assertThat(got)
        .as("div mismatch: %s / %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  private void assertMulMatchesBigInteger(final BigInteger a, final BigInteger b) {
    UInt256 ua = bigIntegerToUInt256(a);
    UInt256 ub = bigIntegerToUInt256(b);

    byte[] got = ua.mul(ub).toBytesBE();

    byte[] expected = bigUnsignedToBytes32(a.multiply(b).mod(TWO_256));
    assertThat(got)
        .as("mul mismatch: %s * %s", ua.toHexString(), ub.toHexString())
        .containsExactly(expected);
  }

  private static UInt256 bigIntegerToUInt256(final BigInteger val) {
    return UInt256.fromBytesBE(bigUnsignedToBytes32(val.mod(TWO_256)));
  }

  private static byte[] clampUnsigned32(final byte[] any) {
    if (any.length == 0) {
      return new byte[] {0};
    }
    int len = Math.max(1, Math.min(32, any.length));
    byte[] out = new byte[len];
    System.arraycopy(any, 0, out, 0, len);
    return out;
  }

  private static byte[] bigUnsignedToBytes32(final BigInteger x) {
    BigInteger y = x.mod(TWO_256);

    byte[] ba = y.toByteArray();
    if (ba.length == 0) {
      return new byte[] {0};
    }

    if (ba.length == 32) {
      return ba;
    }

    if (ba.length < 32) {
      byte[] out = new byte[32];
      System.arraycopy(ba, 0, out, 32 - ba.length, ba.length);
      return out;
    }

    byte[] out = new byte[32];
    System.arraycopy(ba, ba.length - 32, out, 0, 32);
    return out;
  }

  private static BigInteger toBigUnsigned(final byte[] be) {
    return new BigInteger(1, be);
  }

  private static BigInteger minSigned256() {
    // -2^255 in two's complement = 0x8000...0000
    return BigInteger.ONE.shiftLeft(255).negate();
  }

  private static byte[] bigSignedToBytes32(final BigInteger val) {
    // Convert signed BigInteger to 256-bit two's complement bytes
    BigInteger mod = val.mod(TWO_256);
    return bigUnsignedToBytes32(mod);
  }

  // endregion
}
