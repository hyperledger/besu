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
package org.hyperledger.besu.evm;

import java.math.BigInteger;

/**
 * 256-bits wide unsigned integer class.
 *
 * <p>This class is an optimised version of BigInteger for fixed width 256-bits integers.
 */
public record UInt256(long u3, long u2, long u1, long u0) {

  // region Values
  // --------------------------------------------------------------------------
  // UInt256 represents a big-endian 256-bits integer.
  // As opposed to Java int, operations are by default unsigned,
  // and signed version are interpreted in two-complements as usual.
  // offset is used to optimise algorithms, skipping leading zeroes.
  // Nonetheless, 256bits are always allocated and initialised to zeroes.

  /** Fixed size in bytes. */
  public static final int BYTESIZE = 32;

  /** Fixed size in bits. */
  public static final int BITSIZE = 256;

  /** The constant 0. */
  public static final UInt256 ZERO = new UInt256(0, 0, 0, 0);

  /** The constant All ones */
  public static final UInt256 MAX =
      new UInt256(
          0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);

  // --------------------------------------------------------------------------
  // endregion

  // region (private) Internal Values
  // --------------------------------------------------------------------------

  // Fixed number of limbs or digits
  // private static final int N_LIMBS = 4;
  // Fixed number of bits per limb.
  private static final int N_BITS_PER_LIMB = 64;

  // Arrays of zeros.
  // We accomodate up to a result of a multiplication
  // private static final long[] ZERO_LONGS = new long[9];

  // --------------------------------------------------------------------------
  // endregion

  // region Alternative Constructors
  // --------------------------------------------------------------------------

  /**
   * Instantiates a new UInt256 from byte array.
   *
   * @param bytes raw bytes in BigEndian order.
   * @return Big-endian UInt256 represented by the bytes.
   */
  public static UInt256 fromBytesBE(final byte[] bytes) {
    if (bytes.length == 0) return ZERO;
    long u3 = 0;
    long u2 = 0;
    long u1 = 0;
    long u0 = 0;
    int b = bytes.length - 1; // Index in bytes array
    for (int j = 0, shift = 0; j < 8 && b >= 0; j++, b--, shift += 8) {
      u0 |= ((bytes[b] & 0xFFL) << shift);
    }
    for (int j = 0, shift = 0; j < 8 && b >= 0; j++, b--, shift += 8) {
      u1 |= ((bytes[b] & 0xFFL) << shift);
    }
    for (int j = 0, shift = 0; j < 8 && b >= 0; j++, b--, shift += 8) {
      u2 |= ((bytes[b] & 0xFFL) << shift);
    }
    for (int j = 0, shift = 0; j < 8 && b >= 0; j++, b--, shift += 8) {
      u3 |= ((bytes[b] & 0xFFL) << shift);
    }
    return new UInt256(u3, u2, u1, u0);
  }

  /**
   * Instantiates a new UInt256 from an int.
   *
   * @param value int value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromInt(final int value) {
    return new UInt256(0, 0, 0, value & 0xFFFFFFFFL);
  }

  /**
   * Instantiates a new UInt256 from a long.
   *
   * @param value long value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromLong(final long value) {
    return new UInt256(0, 0, 0, value);
  }

  public static UInt256 fromArray(final long[] limbs) {
    int i = limbs.length;
    long z0 = limbs[--i];
    long z1 = limbs[--i];
    long z2 = limbs[--i];
    long z3 = limbs[--i];
    return new UInt256(z3, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Conversions
  // --------------------------------------------------------------------------
  /**
   * Convert to int.
   *
   * @return Value truncated to an int, possibly lossy.
   */
  public int intValue() {
    return (int) u0;
  }

  /**
   * Convert to long.
   *
   * @return Value truncated to a long, possibly lossy.
   */
  public long longValue() {
    return u0;
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBE() {
    byte[] result = new byte[BYTESIZE];
    longIntoBytes(result, 0, u3);
    longIntoBytes(result, 8, u2);
    longIntoBytes(result, 16, u1);
    longIntoBytes(result, 24, u0);
    return result;
  }

  // Helper method to write 8 bytes from big-endian int
  private static void longIntoBytes(final byte[] bytes, final int offset, final long value) {
    bytes[offset] = (byte) (value >>> 56);
    bytes[offset + 1] = (byte) (value >>> 48);
    bytes[offset + 2] = (byte) (value >>> 40);
    bytes[offset + 3] = (byte) (value >>> 32);
    bytes[offset + 4] = (byte) (value >>> 24);
    bytes[offset + 5] = (byte) (value >>> 16);
    bytes[offset + 6] = (byte) (value >>> 8);
    bytes[offset + 7] = (byte) value;
  }

  /**
   * Convert to BigInteger.
   *
   * @return BigInteger representing the integer.
   */
  public BigInteger toBigInteger() {
    return new BigInteger(1, toBytesBE());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("0x");
    for (byte b : toBytesBE()) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  public void intoArray(final long[] limbs) {
    int len = limbs.length;
    limbs[len--] = u0;
    limbs[len--] = u1;
    limbs[len--] = u2;
    limbs[len--] = u3;
  }

  private UInt320 UInt320Value() {
    return new UInt320(0, u3, u2, u1, u0);
  }
  // --------------------------------------------------------------------------
  // endregion

  // region Comparisons
  // --------------------------------------------------------------------------

  /**
   * Is the value 0 ?
   *
   * @return true if this UInt256 value is 0.
   */
  public boolean isZero() {
    return (u0 | u1 | u2 | u3) == 0;
  }

  /**
   * Is the value 1 ?
   *
   * @return true if this UInt256 value is 1.
   */
  public boolean isOne() {
    return ((u0 ^ 1L) | u1 | u2 | u3) == 0;
  }

  /**
   * Is the value 0 or 1 ?
   *
   * @return true if this UInt256 value is 1.
   */
  public boolean isZeroOrOne() {
    return ((u0 & -2L) | u1 | u2 | u3) == 0;
  }

  /**
   * Is the two complements signed representation of this integer negative.
   *
   * @return True if the two complements representation of this integer is negative.
   */
  public boolean isNegative() {
    return u3 < 0;
  }

  /**
   * Does the value fit a long.
   *
   * @return true if this UInt256 value is 1 limb.
   */
  public boolean isUInt64() {
    return (u1 | u2 | u3) == 0;
  }

  public boolean isUint128() {
    return (u2 | u3) == 0;
  }

  public boolean isUint192() {
    return u3 == 0;
  }

  /**
   * Compares two UInt256.
   *
   * @param a left UInt256
   * @param b right UInt256
   * @return 0 if a == b, negative if a &lt; b and positive if a &gt; b.
   */
  public static int compare(final UInt256 a, final UInt256 b) {
    if (a.u3 != b.u3) return Long.compareUnsigned(a.u3, b.u3);
    if (a.u2 != b.u2) return Long.compareUnsigned(a.u2, b.u2);
    if (a.u1 != b.u1) return Long.compareUnsigned(a.u1, b.u1);
    return Long.compareUnsigned(a.u0, b.u0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Bitwise Operations
  // --------------------------------------------------------------------------

  /**
   * Bitwise AND operation
   *
   * @param other The UInt256 to AND with this.
   * @return The UInt256 result from the bitwise AND operation
   */
  public UInt256 and(final UInt256 other) {
    return new UInt256(u3 & other.u3, u2 & other.u2, u1 & other.u1, u0 & other.u0);
  }

  /**
   * Bitwise XOR operation
   *
   * @param other The UInt256 to XOR with this.
   * @return The UInt256 result from the bitwise XOR operation
   */
  public UInt256 xor(final UInt256 other) {
    return new UInt256(u3 ^ other.u3, u2 ^ other.u2, u1 ^ other.u1, u0 ^ other.u0);
  }

  /**
   * Bitwise OR operation
   *
   * @param other The UInt256 to OR with this.
   * @return The UInt256 result from the bitwise OR operation
   */
  public UInt256 or(final UInt256 other) {
    return new UInt256(u3 | other.u3, u2 | other.u2, u1 | other.u1, u0 | other.u0);
  }

  /**
   * Bitwise NOT operation
   *
   * @return The UInt256 result from the bitwise NOT operation
   */
  public UInt256 not() {
    return new UInt256(~u3, ~u2, ~u1, ~u0);
  }

  /**
   * Bitwise shift left.
   *
   * @param shift The number of bits to shift left (at most 64).
   * @return The shifted UInt256.
   */
  public UInt256 shiftLeft(final int shift) {
    // Unchecked: 0 <= shift < 64
    if (shift == 0) return this;
    int invShift = (N_BITS_PER_LIMB - shift);
    long z0 = (u0 << shift);
    long z1 = (u1 << shift) | u0 >>> invShift;
    long z2 = (u2 << shift) | u1 >>> invShift;
    long z3 = (u3 << shift) | u2 >>> invShift;
    return new UInt256(z3, z2, z1, z0);
  }

  /**
   * Bitwise shift right.
   *
   * @param shift The number of bits to shift right (at most 64).
   * @return The shifted UInt256.
   */
  public UInt256 shiftRight(final int shift) {
    // Unchecked: 0 <= shift < 64
    if (shift == 0) return this;
    int invShift = (N_BITS_PER_LIMB - shift);
    long z3 = (u3 >>> shift);
    long z2 = (u2 >>> shift) | u3 << invShift;
    long z1 = (u1 >>> shift) | u2 << invShift;
    long z0 = (u0 >>> shift) | u1 << invShift;
    return new UInt256(z3, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Arithmetic Operations
  // --------------------------------------------------------------------------

  /**
   * Compute the two-complement negative representation of this integer.
   *
   * @return The negative of this integer.
   */
  public UInt256 neg() {
    long carry = 1;
    long z0 = ~u0 + carry;
    carry = (z0 == 0 && carry == 1) ? 1 : 0;
    long z1 = ~u1 + carry;
    carry = (z1 == 0 && carry == 1) ? 1 : 0;
    long z2 = ~u2 + carry;
    carry = (z2 == 0 && carry == 1) ? 1 : 0;
    long z3 = ~u3 + carry;
    return new UInt256(z3, z2, z1, z0);
  }

  /**
   * Compute the absolute value for a two-complement negative representation of this integer.
   *
   * @return The absolute value of this integer.
   */
  public UInt256 abs() {
    return isNegative() ? neg() : this;
  }
  /**
   * Addition
   *
   * <p>Compute dividend (mod modulus) as unsigned big-endian integer.
   *
   * @param other Integer to add to this integer.
   * @return The sum.
   */
  public UInt256 add(final UInt256 other) {
    long carry;
    if (isZero()) return other;
    if (other.isZero()) return this;
    long z0 = u0 + other.u0;
    carry = (Long.compareUnsigned(u0, z0) > 0) ? 1 : 0;
    long z1 = u1 + other.u1 + carry;
    carry = (Long.compareUnsigned(u1, z1) > 0) ? 1 : 0;
    long z2 = u2 + other.u2 + carry;
    carry = (Long.compareUnsigned(u2, z2) > 0) ? 1 : 0;
    long z3 = u3 + other.u3 + carry;
    return new UInt256(z3, z2, z1, z0);
  }

  /**
   * Unsigned modulo reduction.
   *
   * <p>Compute dividend (mod modulus) as unsigned big-endian integer.
   *
   * @param modulus The modulus of the reduction.
   * @return The remainder.
   */
  public UInt256 mod(final UInt256 modulus) {
    if (isZero() || modulus.isZeroOrOne()) return ZERO;
    int cmp = compare(this, modulus);
    if (cmp == 0) return ZERO;
    if (cmp < 0) return this;

    if (modulus.u3 != 0) return modulus.applyKnuth256To256(this);
    if (modulus.u2 != 0) return modulus.applyKnuth192To256(this);
    if (modulus.u1 != 0) return modulus.applyKnuth128To256(this);
    return modulus.applyKnuth64To256(this);
  }

  /**
   * Signed modulo reduction.
   *
   * <p>In signed modulo reduction, integers are interpretated as fixed 256 bits width two's
   * complement signed integers.
   *
   * @param modulus The modulus of the reduction
   * @return The remainder modulo {@code modulus}.
   */
  public UInt256 signedMod(final UInt256 modulus) {
    if (isZero() || modulus.isZeroOrOne() || modulus.equals(MAX)) return ZERO;
    UInt256 a = abs();
    UInt256 m = modulus.abs();
    UInt256 r = a.mod(m);
    if (isNegative()) r = r.neg();
    return r;
  }

  /**
   * Modular addition.
   *
   * @param other The integer to add to this.
   * @param modulus The modulus of the reduction.
   * @return This integer this + other (mod modulus).
   */
  public UInt256 addMod(final UInt256 other, final UInt256 modulus) {
    if (isZero()) return other.mod(modulus);
    if (other.isZero()) return this.mod(modulus);
    if (modulus.isZeroOrOne()) return ZERO;
    UInt257 sum = adc(other);
    if (modulus.u3 != 0) return modulus.applyKnuth256To257(sum);
    if (modulus.u2 != 0) return modulus.applyKnuth192To257(sum);
    if (modulus.u1 != 0) return modulus.applyKnuth128To257(sum);
    return modulus.applyKnuth64To257(sum);
  }

  /**
   * Modular multiplication.
   *
   * @param other The integer to add to this.
   * @param modulus The modulus of the reduction.
   * @return This integer this + other (mod modulus).
   */
  public UInt256 mulMod(final UInt256 other, final UInt256 modulus) {
    if (this.isZero() || other.isZero() || modulus.isZeroOrOne()) return ZERO;
    if (this.isOne()) return other.mod(modulus);
    if (other.isOne()) return other.mod(modulus);
    if (modulus.u3 != 0) return modulus.applyMod256ToMul(this, other);
    if (modulus.u2 != 0) return modulus.applyMod192ToMul(this, other);
    if (modulus.u1 != 0) return modulus.applyMod128ToMul(this, other);
    return modulus.applyMod64ToMul(this, other);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private basic operations
  //
  // adc (add and carry): carry, a <- a + b
  // mac (multiply accumulate): a <- a + b * c + carryIn
  // --------------------------------------------------------------------------

  private static UInt256 fromUInt320High(final UInt320 value) {
    return new UInt256(value.u4, value.u3, value.u2, value.u1);
  }

  private UInt320 shiftLeft256(final int shift) {
    if (shift == 0) return UInt320Value();
    int invShift = (N_BITS_PER_LIMB - shift);
    long z0 = (u0 << shift);
    long z1 = (u1 << shift) | u0 >>> invShift;
    long z2 = (u2 << shift) | u1 >>> invShift;
    long z3 = (u3 << shift) | u2 >>> invShift;
    long z4 =  u3 >>> invShift;
    return new UInt320(z4, z3, z2, z1, z0);
  }

  private UInt320 shiftLeft257(final int shift) {
    // Same as for UInt256, but with implicit 257 bit set
    if (shift == 0) return UInt320Value();
    int invShift = (N_BITS_PER_LIMB - shift);
    long z0 = (u0 << shift);
    long z1 = (u1 << shift) | u0 >>> invShift;
    long z2 = (u2 << shift) | u1 >>> invShift;
    long z3 = (u3 << shift) | u2 >>> invShift;
    long z4 =  u3 >>> invShift;
    z4 += 1L << shift;
    return new UInt320(z4, z3, z2, z1, z0);
  }

  private UInt256 shiftRightDigits() {
    return new UInt256(0, u3, u2, u1);
  }

  private UInt257 adc(final UInt256 other) {
    boolean carry;
    if (isZero()) return new UInt257(false, other);
    if (other.isZero()) return new UInt257(false, this);
    long z0 = u0 + other.u0;
    carry = Long.compareUnsigned(u0, z0) > 0;
    long z1 = u1 + other.u1 + (carry ? 1L : 0L);
    carry = Long.compareUnsigned(u1, z1) > 0;
    long z2 = u2 + other.u2 + (carry ? 1L : 0L);
    carry = Long.compareUnsigned(u2, z2) > 0;
    long z3 = u3 + other.u3 + (carry ? 1L : 0L);
    carry = Long.compareUnsigned(u3, z3) > 0;
    return new UInt257(carry, new UInt256(z3, z2, z1, z0));
  }

  private UInt256 mac128(final long multiplier, final UInt256 carryIn) {
    // result = this * multiplier + carryIn shifted by 1
    long hi, lo, carry;

    if (multiplier == 0) return carryIn.shiftRightDigits();

    lo = u0 * multiplier;
    hi = Math.unsignedMultiplyHigh(u0, multiplier);
    long z0 = lo + carryIn.u1;
    hi += (Long.compareUnsigned(z0, lo) < 0) ? 1 : 0;

    long z1 = hi + carryIn.u2;
    carry = (Long.compareUnsigned(z1, hi) < 0) ? 1 : 0;
    lo = u1 * multiplier;
    hi = Math.unsignedMultiplyHigh(u1, multiplier);
    z1 += lo;
    hi += (Long.compareUnsigned(z1, lo) < 0) ? carry + 1 : carry;

    return new UInt256(0, hi, z1, z0);
  }

  private UInt256 mac192(final long multiplier, final UInt256 carryIn) {
    // result = this * multiplier + carryIn shifted by 1
    long hi, lo, carry;

    if (multiplier == 0) return carryIn.shiftRightDigits();

    lo = u0 * multiplier;
    hi = Math.unsignedMultiplyHigh(u0, multiplier);
    long z0 = lo + carryIn.u1;
    hi += (Long.compareUnsigned(z0, lo) < 0) ? 1 : 0;

    long z1 = hi + carryIn.u2;
    carry = (Long.compareUnsigned(z1, hi) < 0) ? 1 : 0;
    lo = u1 * multiplier;
    hi = Math.unsignedMultiplyHigh(u1, multiplier);
    z1 += lo;
    hi += (Long.compareUnsigned(z1, lo) < 0) ? carry + 1 : carry;

    long z2 = hi + carryIn.u3;
    carry = (Long.compareUnsigned(z2, hi) < 0) ? 1 : 0;
    lo = u2 * multiplier;
    hi = Math.unsignedMultiplyHigh(u2, multiplier);
    z2 += lo;
    hi += (Long.compareUnsigned(z2, lo) < 0) ? carry + 1 : carry;

    return new UInt256(hi, z2, z1, z0);
  }

  private UInt320 mac256(final long multiplier, final UInt256 carryIn) {
    // result = this * multiplier + carryIn
    long hi, lo, carry;

    if (multiplier == 0) return carryIn.UInt320Value();

    lo = u0 * multiplier;
    hi = Math.unsignedMultiplyHigh(u0, multiplier);
    long z0 = lo + carryIn.u0;
    hi += (Long.compareUnsigned(z0, lo) < 0) ? 1 : 0;
    carry = 0;

    long z1 = hi + carryIn.u1;
    carry = (Long.compareUnsigned(z1, hi) < 0) ? 1 : 0;
    lo = u1 * multiplier;
    hi = Math.unsignedMultiplyHigh(u1, multiplier);
    z1 += lo;
    hi += (Long.compareUnsigned(z1, lo) < 0) ? carry + 1 : carry;

    long z2 = hi + carryIn.u2;
    carry = (Long.compareUnsigned(z2, hi) < 0) ? 1 : 0;
    lo = u2 * multiplier;
    hi = Math.unsignedMultiplyHigh(u2, multiplier);
    z2 += lo;
    hi += (Long.compareUnsigned(z2, lo) < 0) ? carry + 1 : carry;

    long z3 = hi + carryIn.u3;
    carry = (Long.compareUnsigned(z3, hi) < 0) ? 1 : 0;
    lo = u3 * multiplier;
    hi = Math.unsignedMultiplyHigh(u3, multiplier);
    z3 += lo;
    hi += (Long.compareUnsigned(z3, lo) < 0) ? carry + 1 : carry;

    return new UInt320(hi, z3, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private multiplication
  // --------------------------------------------------------------------------

  private UInt256 mul128(long v1, long v0) {
    long z0;
    UInt256 res;

    res = mac128(v0, ZERO);
    z0 = res.u0;
    res = mac128(v1, res);

    return new UInt256(res.u2, res.u1, res.u0, z0);
  }

  private UInt384 mul192(long v2, long v1, long v0) {
    long z1, z0;
    UInt256 res;

    res = mac192(v0, ZERO);
    z0 = res.u0;
    res = mac192(v1, res);
    z1 = res.u0;
    res = mac192(v2, res);

    return new UInt384(res.u3, res.u2, res.u1, res.u0, z1, z0);
  }

  private UInt512 mul256(final UInt256 other) {
    long z2, z1, z0;
    UInt320 res;
    res = mac256(other.u0, ZERO);
    z0 = res.u0;
    res = mac256(other.u1, UInt256.fromUInt320High(res));
    z1 = res.u0;
    res = mac256(other.u2, UInt256.fromUInt320High(res));
    z2 = res.u0;
    res = mac256(other.u3, UInt256.fromUInt320High(res));
    return new UInt512(res.u4, res.u3, res.u2, res.u1, res.u0, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private quotient estimation
  // --------------------------------------------------------------------------

  // Lookup table for $\floor{\frac{2^{19} -3 ⋅ 2^8}{d_9 - 256}}$
  private static final short[] LUT =
      new short[] {
        2045, 2037, 2029, 2021, 2013, 2005, 1998, 1990, 1983, 1975, 1968, 1960, 1953, 1946, 1938,
        1931, 1924, 1917, 1910, 1903, 1896, 1889, 1883, 1876, 1869, 1863, 1856, 1849, 1843, 1836,
        1830, 1824, 1817, 1811, 1805, 1799, 1792, 1786, 1780, 1774, 1768, 1762, 1756, 1750, 1745,
        1739, 1733, 1727, 1722, 1716, 1710, 1705, 1699, 1694, 1688, 1683, 1677, 1672, 1667, 1661,
        1656, 1651, 1646, 1641, 1636, 1630, 1625, 1620, 1615, 1610, 1605, 1600, 1596, 1591, 1586,
        1581, 1576, 1572, 1567, 1562, 1558, 1553, 1548, 1544, 1539, 1535, 1530, 1526, 1521, 1517,
        1513, 1508, 1504, 1500, 1495, 1491, 1487, 1483, 1478, 1474, 1470, 1466, 1462, 1458, 1454,
        1450, 1446, 1442, 1438, 1434, 1430, 1426, 1422, 1418, 1414, 1411, 1407, 1403, 1399, 1396,
        1392, 1388, 1384, 1381, 1377, 1374, 1370, 1366, 1363, 1359, 1356, 1352, 1349, 1345, 1342,
        1338, 1335, 1332, 1328, 1325, 1322, 1318, 1315, 1312, 1308, 1305, 1302, 1299, 1295, 1292,
        1289, 1286, 1283, 1280, 1276, 1273, 1270, 1267, 1264, 1261, 1258, 1255, 1252, 1249, 1246,
        1243, 1240, 1237, 1234, 1231, 1228, 1226, 1223, 1220, 1217, 1214, 1211, 1209, 1206, 1203,
        1200, 1197, 1195, 1192, 1189, 1187, 1184, 1181, 1179, 1176, 1173, 1171, 1168, 1165, 1163,
        1160, 1158, 1155, 1153, 1150, 1148, 1145, 1143, 1140, 1138, 1135, 1133, 1130, 1128, 1125,
        1123, 1121, 1118, 1116, 1113, 1111, 1109, 1106, 1104, 1102, 1099, 1097, 1095, 1092, 1090,
        1088, 1086, 1083, 1081, 1079, 1077, 1074, 1072, 1070, 1068, 1066, 1064, 1061, 1059, 1057,
        1055, 1053, 1051, 1049, 1047, 1044, 1042, 1040, 1038, 1036, 1034, 1032, 1030, 1028, 1026,
        1024,
      };

  private static long reciprocal(final long x) {
    // Unchecked: x >= (1 << 63)
    long x0 = x & 1L;
    int x9 = (int) (x >>> 55);
    long x40 = 1 + (x >>> 24);
    long x63 = (x + 1) >>> 1;
    long v0 = LUT[x9 - 256] & 0xFFFFL;
    long v1 = (v0 << 11) - ((v0 * v0 * x40) >>> 40) - 1;
    long v2 = (v1 << 13) + ((v1 * ((1L << 60) - v1 * x40)) >>> 47);
    long e = ((v2 >>> 1) & (-x0)) - v2 * x63;
    long s = Math.unsignedMultiplyHigh(v2, e);
    long v3 = (s >>> 1) + (v2 << 31);
    long t0 = v3 * x;
    long t1 = Math.unsignedMultiplyHigh(v3, x);
    t0 += x;
    if (Long.compareUnsigned(t0, x) < 0) t1++;
    t1 += x;
    long v4 = v3 - t1;
    return v4;
  }

  private static long reciprocal2(final long x1, final long x0) {
    // Unchecked: <x1, x0> >= (1 << 127)
    long v = reciprocal(x1);
    long p = x1 * v + x0;
    if (Long.compareUnsigned(p, x0) < 0) {
      v--;
      if (Long.compareUnsigned(p, x1) >= 0) {
        v--;
        p -= x1;
      }
      p -= x1;
    }
    long t0 = v * x0;
    long t1 = Math.unsignedMultiplyHigh(v, x0);
    p += t1;
    if (Long.compareUnsigned(p, t1) < 0) {
      v--;
      int cmp = Long.compareUnsigned(p, x1);
      if ((cmp > 0) || ((cmp == 0) && (Long.compareUnsigned(t0, x0) >= 0))) v--;
    }
    return v;
  }

  private static DivEstimate div2by1(final long x1, final long x0, final long y, final long yInv) {
    long z1 = x1;
    long z0 = x0;

    // wrapping umul z1 * yInv
    long q0 = z1 * yInv;
    long q1 = Math.unsignedMultiplyHigh(z1, yInv);

    // wrapping uadd <q1, q0> + <z1, z0> + <1, 0>
    long sum = q0 + z0;
    long carry = ((q0 & z0) | ((q0 | z0) & ~sum)) >>> 63;
    q0 = sum;
    q1 += z1 + carry + 1;

    z0 -= q1 * y;
    if (Long.compareUnsigned(z0, q0) > 0) {
      q1 -= 1;
      z0 += y;
    }
    if (Long.compareUnsigned(z0, y) >= 0) {
      q1 += 1;
      z0 -= y;
    }
    return new DivEstimate(q1, z0);
   }

  private static long mod2by1(final long x1, final long x0, final long y, final long yInv) {
    long z1 = x1;
    long z0 = x0;
    // wrapping umul z1 * yInv
    long q0 = z1 * yInv;
    long q1 = Math.unsignedMultiplyHigh(z1, yInv);

    // wrapping uadd <q1, q0> + <z1, z0> + <1, 0>
    long sum = q0 + z0;
    long carry = ((q0 & z0) | ((q0 | z0) & ~sum)) >>> 63;
    q0 = sum;
    q1 += z1 + carry + 1;

    z0 -= q1 * y;
    if (Long.compareUnsigned(z0, q0) > 0) {
      q1 -= 1;
      z0 += y;
    }
    if (Long.compareUnsigned(z0, y) >= 0) {
      q1 += 1;
      z0 -= y;
    }
    return z0;
  }

  private static Div2Estimate div3by2(
      final long x2, final long x1, final long x0, final long y1, final long y0, final long yInv) {
    // <x2, x1, x0> divided by <y1, y0>.
    // Requires <x2, x1> < <y1, y0> otherwise quotient overflows.
    long overflow; // carry or borrow
    long res; // sum or diff
    long z2 = x2;
    long z1 = x1;
    long z0 = x0;

    // <q1, q0> = z2 * yInv + <z2, z1>
    long q0 = z2 * yInv;
    long q1 = Math.unsignedMultiplyHigh(z2, yInv);
    res = q0 + z1;
    overflow = ((q0 & z1) | ((q0 | z1) & ~res)) >>> 63;
    q0 = res;
    q1 = q1 + z2 + overflow;

    // r1 <- z1 - q1 * y1 mod B
    z1 -= q1 * y1;

    // wrapping sub <r1, z0> − q1*y0 − <y1, y0>
    long t0 = q1 * y0;
    long t1 = Math.unsignedMultiplyHigh(q1, y0);

    res = z0 - t0;
    overflow = ((~z0 & t0) | ((~z0 | t0) & res)) >>> 63;
    z0 = res;
    z1 -= (t1 + overflow);

    res = z0 - y0;
    overflow = ((~z0 & y0) | ((~z0 | y0) & res)) >>> 63;
    z0 = res;
    z1 -= (y1 + overflow);

    // Adjustments
    q1 += 1;
    if (Long.compareUnsigned(z1, q0) >= 0) {
      q1 -= 1;
      res = z0 + y0;
      overflow = ((z0 & y0) | ((z0 | y0) & ~res)) >>> 63;
      z0 = res;
      z1 += y1 + overflow;
    }

    int cmp = Long.compareUnsigned(z1, y1);
    if ((cmp > 0) || ((cmp == 0) && (Long.compareUnsigned(z0, y0) >= 0))) {
      q1 += 1;
      res = z0 - y0;
      overflow = ((~z0 & y0) | ((~z0 | y0) & res)) >>> 63;
      z0 = res;
      z1 -= (y1 + overflow);
    }
    return new Div2Estimate(q1, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private reduction step
  //
  // A reduction step substract's a multiple of the modulus to zero the most
  // significant digit.
  // --------------------------------------------------------------------------

  private long mod64Step(final long v1, final long v0, final long inv) {
    return (v1 == u1) ? Long.remainderUnsigned(v0, u0) : div2by1(v1, v0, u0, inv).r;
  }

  private UInt128 mod128Step(final long v2, final long v1, final long v0, final long inv) {
    long borrow, p1, res;
    long z2 = v2;
    long z1 = v1;
    long z0 = v0;

    if (z2 == u1 && z1 == u0) {
      // Overflow case: div3by2 quotient would be <1, 0>, but adjusts to <0, -1>
      // <p1, p0> = -1 * u0 = <u0 - 1, -u0>
      res = z0 + u0;
      borrow = ((~z0 & ~u0) | ((~z0 | ~u0) & res)) >>> 63;
      p1 = u0 - 1 + borrow;
      z0 = res;
      z1 = z1 - p1 + u1;
    } else {
      Div2Estimate qr = div3by2(z2, z1, z0, u1, u0, inv);
      z1 = qr.r1;
      z0 = qr.r0;
    }
    return new UInt128(z1, z0);
  }

  private UInt192 mod192Step (
    final long v3, final long v2, final long v1, final long v0, final long inv) {
    long borrow, p0, p1, res;
    // Divide step -> get highest 2 limbs.
    long z3 = v3;
    long z2 = v2;
    long z1 = v1;
    long z0 = v0;

    if (z3 == u2 && z2 == u1) {
      // Overflow case: div3by2 quotient would be <1, 0>, but adjusts to <0, -1>
      // <p1, p0> = -1 * u0 = <u0 - 1, -u0>
      res = z0 + u0;
      borrow = ((~z0 & ~u0) | ((~z0 | ~u0) & res)) >>> 63;
      p1 = u0 - 1 + borrow;
      z0 = res;

      res = z1 - p1;
      borrow = ((~z1 & p1) | ((~z1 | p1) & res)) >>> 63;
      p1 = u1 - 1 + borrow;
      z1 = res + u1;
      borrow = ((~res & ~u1) | ((~res | ~u1) & z1)) >>> 63;

      z2 = z2 - p1 + u2 - borrow;
      // borrow = ((~z2 & p1) | ((~z2 | p1) & res)) >>> 63;
      // p1 = u2 - 1 + borrow;
      // borrow = ((~res & ~u1) | ((~res | ~u1) & z1)) >>> 63;
      // assert p1 + borrow == z3 : "Division did not cancel top digit"
    } else {
      Div2Estimate qr = div3by2(z3, z2, z1, u2, u1, inv);
      z3 = 0;
      z2 = qr.r1;
      z1 = qr.r0;

      if (qr.q != 0) {
        // Multiply-subtract: already have highest 2 limbs
        // <u4, u3, u2, u1>  =  <u2, u1, u0> * q
        p0 = u0 * qr.q;
        p1 = Math.unsignedMultiplyHigh(u0, qr.q);
        res = z0 - p0;
        p1 += ((~z0 & p0) | ((~z0 | p0) & res)) >>> 63;
        z0 = res;

        // Propagate overflows (borrows)
        res = z1 - p1;
        borrow = ((~z1 & p1) | ((~z1 | p1) & res)) >>> 63;
        z1 = res;

        res = z2 - borrow;
        borrow = (((borrow ^ 1L) | z2) == 0) ? 1 : 0;
        z2 = res;

        z3 -= borrow;
        borrow = (z3 >>> 63) & 1;

        if (borrow != 0) { // unlikely
          // Add back
          long carry = 0;
          res = z0 + u0 + carry;
          carry = ((z0 & u0) | ((z0 | u0) & ~res)) >>> 63;
          z0 = res;
          res = z1 + u1 + carry;
          carry = ((z1 & u1) | ((z1 | u1) & ~res)) >>> 63;
          z1 = res;
          res = z2 + u2 + carry;
          carry = ((z2 & u2) | ((z2 | u2) & ~res)) >>> 63;
          z2 = res;
          // z3 = z3 + carry;
        }
      }
    }
    return new UInt192(z2, z1, z0);
  }

  private UInt256 mod256Step(
    final long v4, final long v3, final long v2, final long v1, final long v0, final long inv) {
    long borrow, p0, p1, p2, res;
    long z4 = v4;
    long z3 = v3;
    long z2 = v2;
    long z1 = v1;
    long z0 = v0;

    if (z4 == u3 && z3 == u2) {
      // Overflow case: div3by2 quotient would be <1, 0>, but adjusts to <0, -1>
      // <p1, p0> = -1 * u0 = <u0 - 1, -u0>
      res = z0 + u0;
      borrow = ((~z0 & ~u0) | ((~z0 | ~u0) & res)) >>> 63;
      p1 = u0 - 1 + borrow;
      z0 = res;

      res = z1 - p1;
      borrow = ((~z1 & p1) | ((~z1 | p1) & res)) >>> 63;
      p1 = u1 - 1 + borrow;
      z1 = res + u1;
      borrow = ((~res & ~u1) | ((~res | ~u1) & z1)) >>> 63;

      res = z2 - p1 - borrow;
      borrow = ((~z2 & p1) | ((~z2 | p1) & res)) >>> 63;
      p1 = u2 - 1 + borrow;
      z2 = res + u2;
      borrow = ((~res & ~u2) | ((~res | ~u2) & z2)) >>> 63;

      z3 = z3 - p1 + u3 - borrow;
    } else {
      Div2Estimate qr = div3by2(z4, z3, z2, u3, u2, inv);
      z3 = qr.r1;
      z2 = qr.r0;

      // Multiply-subtract: already have highest 2 limbs
      // <z4, z3, z2, z1, z0>  =  <u3, u2, u1, u0> * q
      p0 = u0 * qr.q;
      p1 = Math.unsignedMultiplyHigh(u0, qr.q);
      res = z0 - p0;
      p1 += ((~z0 & p0) | ((~z0 | p0) & res)) >>> 63;
      z0 = res;

      p0 = u1 * qr.q;
      p2 = Math.unsignedMultiplyHigh(u1, qr.q);
      res = z1 - p0;
      p2 += ((~z1 & p0) | ((~z1 | p0) & res)) >>> 63;
      z1 = res - p1;
      borrow = ((~res & p1) | ((~res | p1) & z1)) >>> 63;

      // Propagate overflows (borrows)
      res = z2 - p2 - borrow;
      borrow = ((~z2 & p2) | ((~z2 | p2) & res)) >>> 63;
      z2 = res;

      res = z3 - borrow;
      borrow = (((borrow ^ 1L) | z3) == 0) ? 1 : 0;
      z3 = res;

      z4 -= borrow;
      borrow = (z4 >>> 63) & 1;

      if (borrow != 0) { // unlikely
        // Add back
        long carry = 0;
        res = z0 + u0 + carry;
        carry = ((z0 & u0) | ((z0 | u0) & ~res)) >>> 63;
        z0 = res;
        res = z1 + u1 + carry;
        carry = ((z1 & u1) | ((z1 | u1) & ~res)) >>> 63;
        z1 = res;
        res = z2 + u2 + carry;
        carry = ((z2 & u2) | ((z2 | u2) & ~res)) >>> 63;
        z2 = res;
        res = z3 + u3 + carry;
        // carry = ((z3 & u3) | ((z3 | u3) & ~res)) >>> 63;
        z3 = res;
        // z4 = z4 + carry;
      }
    }
    return new UInt256(z3, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private applyMod: this.applyMod(that, inv) -> that (mod this)
  //
  // Modulus always fits 256bits, so we dispatch mod from the modulus:
  // that.applyMod(this, inv) is the integer this (mod that).
  //
  // applyMod methods require that the highest digit of the modulus be negative,
  // that is its highest bit is set. We can then leverage the reciprocal `inv`.
  //
  // We sometimes need to apply several reductions with the same modulus.
  // To be able to reuse the modulus' reciprocal `inv`, we pass it as argument.
  // If modulus is 1 digit, we use reciprocal, otherwise reciprocal2.
  // --------------------------------------------------------------------------

  private UInt256 applyMod64To320(final UInt320 that, final long inv) {
    long r;
    if (that.u4 != 0) {
      r = mod64Step(that.u4, that.u3, inv);
      r = mod64Step(r, that.u2, inv);
      r = mod64Step(r, that.u1, inv);
      r = mod64Step(r, that.u0, inv);
    } else if (u3 != 0) {
      r = mod64Step(that.u3, that.u2, inv);
      r = mod64Step(r, that.u1, inv);
      r = mod64Step(r, that.u0, inv);
    } else if (u2 != 0){
      r = mod64Step(that.u2, that.u1, inv);
      r = mod64Step(r, that.u0, inv);
    } else {
      r = mod64Step(that.u1, that.u0, inv);
    }
    return UInt256.fromLong(r);
  }

  private UInt256 applyMod128To256(final UInt256 that, final long inv) {
    UInt128 r;
    if (u3 != 0) {
      r = mod128Step(that.u3, that.u2, that.u1, inv);
      r = mod128Step(r.u1, r.u0, that.u0, inv);
    } else {
      r = mod128Step(that.u2, that.u1, that.u0, inv);
    }
    return new UInt256(0, 0, r.u1, r.u0);
  }

  private UInt256 applyMod128To320(final UInt320 that, final long inv) {
    UInt128 r;
    if (that.u4 != 0) {
      r = mod128Step(that.u4, that.u3, that.u2, inv);
      r = mod128Step(r.u1, r.u0, that.u1, inv);
      r = mod128Step(r.u1, r.u0, that.u0, inv);
    } else if (u3 != 0) {
      r = mod128Step(that.u3, that.u2, that.u1, inv);
      r = mod128Step(r.u1, r.u0, that.u0, inv);
    } else {
      r = mod128Step(that.u2, that.u1, that.u0, inv);
    }
    return new UInt256(0, 0, r.u1, r.u0);
  }

  private UInt256 applyMod192To320(UInt320 that, long inv) {
    UInt192 r;
    if (that.u4 != 0) {
      r = mod192Step(that.u4, that.u3, that.u2, that.u1, inv);
      r = mod192Step(r.u2, r.u1, r.u0, that.u0, inv);
    } else {
      r = mod192Step(that.u3, that.u2, that.u1, that.u0, inv);
    }
    return new UInt256(0, r.u2, r.u1, r.u0);
  }

  private UInt256 applyMod192To384(UInt384 that, long inv) {
    UInt192 r;
    if (that.u5 != 0) {
      r = mod192Step(that.u5, that.u4, that.u3, that.u2, inv);
      r = mod192Step(r.u2, r.u1, r.u0, that.u1, inv);
      r = mod192Step(r.u2, r.u1, r.u0, that.u0, inv);
    } else if (that.u4 != 0) {
      r = mod192Step(that.u4, that.u3, that.u2, that.u1, inv);
      r = mod192Step(r.u2, r.u1, r.u0, that.u0, inv);
    } else {
      r = mod192Step(that.u3, that.u2, that.u1, that.u0, inv);
    }
    return new UInt256(0, r.u2, r.u1, r.u0);
  }

  private UInt256 applyMod256To320(UInt320 that, long inv) {
    return mod256Step(that.u4, that.u3, that.u2, that.u1, that.u0, inv);
  }

  private UInt256 applyMod256To512(UInt512 that, long inv) {
    UInt256 r;
    if (that.u7 != 0) {
      r = mod256Step(that.u7, that.u6, that.u5, that.u4, that.u3, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u2, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u1, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u0, inv);
    } else if (that.u6 != 0) {
      r = mod256Step(that.u6, that.u5, that.u4, that.u3, that.u2, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u1, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u0, inv);
    } else if (that.u5 != 0){
      r = mod256Step(that.u5, that.u4, that.u3, that.u2, that.u1, inv);
      r = mod256Step(r.u3, r.u2, r.u1, r.u0, that.u0, inv);
    } else {
      r = mod256Step(that.u4, that.u3, that.u2, that.u1, that.u0, inv);
    }
    return r;
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private applyKnuth: this.applyKnuth(that) -> that (mod this)
  //
  // Very much like and actually relies on applyMod, but we shift if needed
  // to satisfy the requirement for applyMod of highest bit for highest digit.
  // --------------------------------------------------------------------------

  private UInt256 applyKnuth64To256(final UInt256 that) {
    // Additional fast path for 1 digit
    if (that.isUInt64()) return UInt256.fromLong(Long.remainderUnsigned(that.u0, u0));
    int shift = Long.numberOfLeadingZeros(u0);
    UInt256 m = UInt256.fromLong(u0 << shift);
    UInt320 a = that.shiftLeft256(shift);
    long inv = reciprocal(m.u0);
    UInt256 r = m.applyMod64To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth64To257(final UInt257 that) {
    // Additional fast path for 1 digit
    if (!that.carry && that.u.isUInt64()) return UInt256.fromLong(Long.remainderUnsigned(that.u.u0, u0));
    int shift = Long.numberOfLeadingZeros(u1);
    UInt256 m = UInt256.fromLong(u0 << shift);
    UInt320 a = that.carry ? that.u.shiftLeft257(shift) : that.u.shiftLeft256(shift);
    long inv = reciprocal(m.u0);
    UInt256 r = m.applyMod64To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth128To256(final UInt256 that) {
    int shift = Long.numberOfLeadingZeros(u1);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.shiftLeft256(shift);
    long inv = UIntMath.reciprocal2(u1, u0);
    UInt256 r = m.applyMod128To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth128To257(final UInt257 that) {
    int shift = Long.numberOfLeadingZeros(u1);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.carry ? that.u.shiftLeft257(shift) : that.u.shiftLeft256(shift);
    long inv = UIntMath.reciprocal2(u1, u0);
    UInt256 r = m.applyMod128To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth192To256(final UInt256 that) {
    int shift = Long.numberOfLeadingZeros(u2);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.shiftLeft256(shift);
    long inv = reciprocal2(u2, u1);
    UInt256 r = m.applyMod192To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth192To257(final UInt257 that) {
    int shift = Long.numberOfLeadingZeros(u2);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.carry ? that.u.shiftLeft257(shift) : that.u.shiftLeft256(shift);
    long inv = reciprocal2(m.u2, m.u1);
    UInt256 r = m.applyMod192To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth256To256(final UInt256 that) {
    int shift = Long.numberOfLeadingZeros(u3);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.shiftLeft256(shift);
    long inv = reciprocal2(m.u3, m.u2);
    UInt256 r = m.applyMod256To320(a, inv);
    return r.shiftRight(shift);
  }

  private UInt256 applyKnuth256To257(final UInt257 that) {
    // precondition: highest digit is 0 or 1;
    // assert (that.u4 & ~1L) == 0;
    int shift = Long.numberOfLeadingZeros(u3);
    UInt256 m = shiftLeft(shift);
    UInt320 a = that.carry ? that.u.shiftLeft257(shift) : that.u.shiftLeft256(shift);
    long inv = reciprocal2(m.u3, m.u2);
    UInt256 r = m.applyMod256To320(a, inv);
    return r.shiftRight(shift);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private applyModToMul: this.applyModToMul(a, b) -> a * b (mod this)
  //
  // We apply modulus reduction to a product:  a * b (mod this)
  // Multiplication is O(MN) in the number of long operations.
  // Reduction is O(M + N) in the number of reduction steps.
  //
  // Applying reduction before multiplication reduces the multiplication size
  // at the cost of a few more shifts and a single additional reduction step.
  // --------------------------------------------------------------------------

  // Mul first then mod: a * b mac, 3 shifts, a + b + 1 - m modStep
  // Mod first then mul: m * m mac, 4 shifts, a + b + 2 - m modStep  (a+1-m + b+1-m + m+m-m)
  // un modstep -> m + 1 mac
  // If b <= m : m * b mac, 5 shifts, a + b + 1 - m modStep
  // m=1 -> a >= 2 and b >= 2  probably more...
  // m=2 -> a >= 3 and b >= 3 or a >= 3 and b == 1
  // m=3 -> at least 4 and 4
  private UInt256 applyMod64ToMul(final UInt256 a, final UInt256 b) {
    int shift = Long.numberOfLeadingZeros(u0);
    UInt256 m = UInt256.fromLong(u0 << shift);
    long inv = reciprocal(m.u0);

    UInt320 x = a.shiftLeft256(shift);
    long x0 = m.applyMod64To320(x, inv).u0;

    UInt320 y = b.shiftLeft256(shift);
    long y0 = m.applyMod64To320(y, inv).u0;

    long p0 = x0 * y0;
    long p1 = Math.unsignedMultiplyHigh(x0, y0);
    long r = m.mod64Step(p1, p0, inv) >>> shift;
    return UInt256.fromLong(r);
  }

  private UInt256 applyMod128ToMul(final UInt256 a, final UInt256 b) {
    int shift = Long.numberOfLeadingZeros(u1);
    UInt256 m = shiftLeft(shift);
    long inv = reciprocal2(m.u1, m.u0);

    UInt320 aShifted = a.shiftLeft256(shift);
    UInt256 x = m.applyMod128To320(aShifted, inv);
    UInt320 bShifted = b.shiftLeft256(shift);
    UInt256 y = m.applyMod128To320(bShifted, inv);

    x = x.mul128(y.u1, y.u0);
    return m.applyMod128To256(x, inv);
  }

  private UInt256 applyMod192ToMul(final UInt256 a, final UInt256 b) {
    int shift = Long.numberOfLeadingZeros(u2);
    UInt256 m = shiftLeft(shift);
    long inv = reciprocal2(m.u2, m.u1);

    UInt320 aShifted = a.shiftLeft256(shift);
    UInt256 x = ((aShifted.u4 | aShifted.u3) != 0) ? m.applyMod192To320(aShifted, inv) : new UInt256(0, aShifted.u2, aShifted.u1, aShifted.u0);
    UInt320 bShifted = b.shiftLeft256(shift);
    UInt256 y = ((bShifted.u4 | bShifted.u3) != 0) ? m.applyMod192To320(bShifted, inv) : new UInt256(0, bShifted.u2, bShifted.u1, bShifted.u0);

    UInt384 prod = x.mul192(y.u2, y.u1, y.u0);
    return m.applyMod192To384(prod, inv).shiftRight(shift);
  }

  private UInt256 applyMod256ToMul(final UInt256 a, final UInt256 b) {
    UInt512 prod = a.mul256(b);
    long inv = reciprocal2(u3, u2);
    return applyMod256To512(prod, inv);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region private records
  // --------------------------------------------------------------------------
  private record UInt128(long u1, long u0) {}
  private record UInt192(long u2, long u1, long u0) {}
  private record UInt257(boolean carry, UInt256 u) {}
  private record UInt320(long u4, long u3, long u2, long u1, long u0) {}
  private record UInt384(long u5, long u4, long u3, long u2, long u1, long u0) {}
  private record UInt512(long u7, long u6, long u5, long u4, long u3, long u2, long u1, long u0) {}
  private record MulResult(UInt256 carry, long u) {}
  private record DivEstimate(long q, long r) {}
  private record Div2Estimate(long q, long r1, long r0) {}

  // --------------------------------------------------------------------------
  // endregion

  // region deprecated
  // --------------------------------------------------------------------------
  // <u4, u3, u2, u1, u0> mod (y0), using yInv = reciprocal(y0)
  // private UInt256 mod1(final long u4, final long y, final long yInv) {
  //   // Unchecked precondition: y msb is set.
  //   // if us >= y, overflows, so must substract y first
  //  // Can happen only with most significant digit ? Not if it was shifted
  //  long r;
  //  if (u4 != 0) {
  //    r = u4;
  //    r = UIntMath.mod2by1(r, u3, y, yInv);
  //    r = UIntMath.mod2by1(r, u2, y, yInv);
  //    r = UIntMath.mod2by1(r, u1, y, yInv);
  //    r = UIntMath.mod2by1(r, u0, y, yInv);
  //  } else if (u3 != 0) {
  //    r = u3;
  //    r = UIntMath.mod2by1(r, u2, y, yInv);
  //    r = UIntMath.mod2by1(r, u1, y, yInv);
  //    r = UIntMath.mod2by1(r, u0, y, yInv);
  //  } else if (u2 != 0) {
  //    r = u2;
  //    r = UIntMath.mod2by1(r, u1, y, yInv);
  //    r = UIntMath.mod2by1(r, u0, y, yInv);
  //  } else if (u1 != 0) {
  //    r = u1;
  //    r = UIntMath.mod2by1(r, u0, y, yInv);
  //  } else {
  //    r = Long.remainderUnsigned(u0, y);
  //  }
  //  return UInt256.fromLong(r);
  //}

  //// <u4, u3, u2, u1, u0> mod (<y1, y0>), using yInv = reciprocal2(y1, y0)
  //private UInt256 mod2(final long u4, final long y1, final long y0, final long yInv) {
  //  UIntMath.Tuple2 r;
  //  if (u4 != 0) {
  //    r = UIntMath.modStep2Digits(u4, u3, u2, y1, y0, yInv);
  //    r = UIntMath.modStep2Digits(r.u1(), r.u0(), u1, y1, y0, yInv);
  //    r = UIntMath.modStep2Digits(r.u1(), r.u0(), u0, y1, y0, yInv);
  //  } else if (u3 != 0) {
  //    r = UIntMath.modStep2Digits(u3, u2, u1, y1, y0, yInv);
  //    r = UIntMath.modStep2Digits(r.u1(), r.u0(), u0, y1, y0, yInv);
  //  } else {
  //    r = UIntMath.modStep2Digits(u2, u1, u0, y1, y0, yInv);
  //  }
  //  return new UInt256(0, 0, r.u1(), r.u0());
  //}

  //// <u4, u3, u2, u1, u0> mod (<y2, y1, y0>), using yInv = reciprocal2(y2, y1)
  //private UInt256 mod3(final long u4, final UInt256 y, final long yInv) {
  //  UIntMath.Tuple3 r;
  //  if (u4 != 0) {
  //    r = UIntMath.modStep3Digits(u4, u3, u2, u1, y.u2, y.u1, y.u0, yInv);
  //    r = UIntMath.modStep3Digits(r.u2(), r.u1(), r.u0(), u0, y.u2, y.u1, y.u0, yInv);
  //  } else {
  //    r = UIntMath.modStep3Digits(u3, u2, u1, u0, y.u2, y.u1, y.u0, yInv);
  //  }
  //  return new UInt256(0, r.u2(), r.u1(), r.u0());
  //}

  //// <u4, u3, u2, u1, u0> mod (<y3, y2, y1, y0>), using yInv = reciprocal2(y3, y2)
  //private UInt256 mod4(final long u4, final UInt256 y, final long yInv) {
  //  UIntMath.Tuple4 r = UIntMath.modStep4Digits(u4, u3, u2, u1, u0, y.u3, y.u2, y.u1, y.u0, yInv);
  //  return new UInt256(r.u3(), r.u2(), r.u1(), r.u0());
  //}

  //private UInt256 knuthRemainder4(final UInt256 modulus, final long carry) {
  //  int shift = Long.numberOfLeadingZeros(modulus.u3);
  //  UInt256 m = modulus.shiftLeft(shift);
  //  UInt256 a = shiftLeft(shift);
  //  long u4 = (shift == 0) ? carry : carry + (u3 >>> (N_BITS_PER_LIMB - shift));
  //  long mInv = reciprocal2(m.u3, m.u2);
  //  UInt256 r = a.mod4(u4, m, mInv);
  //  return r.shiftRight(shift);
  //}

  //private UInt256 knuthRemainder3(final UInt256 modulus, final long carry) {
  //  int shift = Long.numberOfLeadingZeros(modulus.u2);
  //  UInt256 m = modulus.shiftLeft(shift);
  //  UInt256 a = shiftLeft(shift);
  //  long u4 = (shift == 0) ? carry : carry + (u3 >>> (N_BITS_PER_LIMB - shift));
  //  long mInv = UIntMath.reciprocal2(m.u2, m.u1);
  //  UInt256 r = a.mod3(u4, m, mInv);
  //  return r.shiftRight(shift);
  //}

  //private UInt256 knuthRemainder2(final UInt256 modulus, final long carry) {
  //  int shift = Long.numberOfLeadingZeros(modulus.u1);
  //  UInt256 m = modulus.shiftLeft(shift);
  //  UInt256 a = shiftLeft(shift);
  //  long u4 = (shift == 0) ? carry : carry + (u3 >>> (N_BITS_PER_LIMB - shift));
  //  long mInv = UIntMath.reciprocal2(m.u1, m.u0);
  //  UInt256 r = a.mod2(u4, m.u1, m.u0, mInv);
  //  return r.shiftRight(shift);
  //}

  //private UInt256 knuthRemainder1(final UInt256 modulus, final long carry) {
  //  int shift = Long.numberOfLeadingZeros(modulus.u0);
  //  long m0 = modulus.u0 << shift;
  //  long mInv = UIntMath.reciprocal(m0);
  //  UInt256 a = shiftLeft(shift);
  //  long u4 = (shift == 0) ? carry : carry + (u3 >>> (N_BITS_PER_LIMB - shift));
  //  UInt256 r = a.mod1(u4, m0, mInv);
  //  return r.shiftRight(shift);
  //}

  // --------------------------------------------------------------------------
  // endregion
}
