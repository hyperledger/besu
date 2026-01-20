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
  public static final UInt256 MAX = new UInt256(
    0xFFFFFFFFFFFFFFFFL,
    0xFFFFFFFFFFFFFFFFL,
    0xFFFFFFFFFFFFFFFFL,
    0xFFFFFFFFFFFFFFFFL
  );

  // --------------------------------------------------------------------------
  // endregion

  // region (private) Internal Values
  // --------------------------------------------------------------------------

  // Fixed number of limbs or digits
  private static final int N_LIMBS = 4;
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
    return fromBytesBE(bytes, 0);
  }

  /**
   * Instantiates a new UInt256 from byte array.
   *
   * @param bytes raw bytes in BigEndian order.
   * @param offset to ignore leading bytes from array
   * @return Big-endian UInt256 represented by the bytes.
   */
  public static UInt256 fromBytesBE(final byte[] bytes, final int offset) {
    // precondition bytes.length <= 32;
    long[] limbs = new long[N_LIMBS];
    if (bytes.length == 0 || offset == -1 || offset >= bytes.length) return ZERO;
    int i = N_LIMBS - 1; // Index in long array
    int b = bytes.length - 1; // Index in bytes array
    long limb;
    for (; b >= offset; i--) {
      int shift = 0;
      limb = 0;
      for (int j = 0; j < 8 && b >= offset; j++, b--, shift += 8) {
        limb |= ((bytes[b] & 0xFFL) << shift);
      }
      limbs[i] = limb;
    }
    return new UInt256(limbs[0], limbs[1], limbs[2], limbs[3]);
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
  public boolean isUint64() {
    return (u1 | u2 | u3) == 0;
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
    long z0 = (u0 << shift);
    long carry = u0 >>> (N_BITS_PER_LIMB - shift);
    long z1 = (u1 << shift) | carry;
    carry = u1 >>> (N_BITS_PER_LIMB - shift);
    long z2 = (u2 << shift) | carry;
    carry = u2 >>> (N_BITS_PER_LIMB - shift);
    long z3 = (u3 << shift) | carry;
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
    long z3 = (u3 >>> shift);
    long carry = u3 << (N_BITS_PER_LIMB - shift);
    long z2 = (u2 >>> shift) | carry;
    carry = u2 << (N_BITS_PER_LIMB - shift);
    long z1 = (u1 >>> shift) | carry;
    carry = u1 << (N_BITS_PER_LIMB - shift);
    long z0 = (u0 >>> shift) | carry;
    return new UInt256(z3, z2, z1, z0);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Arithmetic Operations
  // --------------------------------------------------------------------------

  /** Compute the two-complement negative representation of this integer.
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

  /** Compute the absolute value for a two-complement negative representation of this integer.
   *
   * @return The absolute value of this integer.
   */
  public UInt256 abs() {
    return isNegative() ? neg() : this;
  }

  /**
   * Unsigned modulo reduction.
   *
   * Compute dividend (mod modulus) as unsigned big-endian integer.
   *
   * @param modulus The modulus of the reduction.
   * @return The remainder.
   */
  public UInt256 mod(final UInt256 modulus) {
    // dividend = 0 or modulus = 0, 1 => return 0
    // compare dividend, modulus:
    //     dividend < modulus => return dividend
    //     dividend == modulus => return 0
    // Convert to longs
    // dividend.isUint64() => return dividend % modulus
    // modulus number of limbs => modNby4, modNby3, modNby2, modNby1;
    if (isZero() || modulus.isZeroOrOne()) return ZERO;
    int cmp = compare(this, modulus);
    if (cmp == 0) return ZERO;
    if (cmp < 0) return this;

    if (isUint64()) return UInt256.fromLong(Long.remainderUnsigned(longValue(), modulus.longValue()));
    if (modulus.u3() != 0) return knuthRemainder4by4(modulus);
    if (modulus.u2() != 0) return knuthRemainder4by3(modulus);
    if (modulus.u1() != 0) return knuthRemainder4by2(modulus);
    return knuthRemainder4by1(modulus);
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
    if (other.isZero()) return mod(modulus);
    if (modulus.isZeroOrOne()) return ZERO;
    long[] sum = new long[N_LIMBS + 1];
    // sum[0] = addImpl(sum, this.limbs, other.limbs);
    // long[] rem = knuthRemainder(sum, modulus.limbs);
    long[] rem = sum;
    return UInt256.fromArray(rem);
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
    // long[] result = addMul(this.limbs, this.offset, other.limbs, other.offset);
    // result = knuthRemainder(result, modulus.limbs);
    // UInt256 res = new UInt256(result, Math.max(0, result.length - modulus.length()));
    return this;
  }

  // --------------------------------------------------------------------------
  // endregion

  /**
   * Simple addition
   *
   * @param other The UInt256 to add to this.
   * @return The UInt256 result from the addition
   */
  public UInt256 add(final UInt256 other) {
    return new UInt256(
        addWithCarry(this.limbs, this.limbs.length, other.limbs, other.limbs.length));
  }

  // region Support (private) Algorithms
  // --------------------------------------------------------------------------

  // Effective length of a big-endian int array: leading zeroes are ignored
  // private static int effectiveLength(final long[] x) {
  //   // Unchecked : x.length <= N_LIMBS
  //   int offset = Arrays.mismatch(x, ZERO_LONGS);
  //   return (offset != -1 || offset != x.length) ? x.length - offset : 0;
  // }
  //
  // Comparing two int subarrays as big-endian multi-precision integers.
  // private static int compareLimbs(final long[] a, final long[] b) {
  //   return (a.length >= b.length) ? compareLimbsSorted(a, b) : -compareLimbsSorted(b, a);
  // }

  // Comparing two int subarrays as big-endian multi-precision integers,
  // where a is known to be longer than b.
  // private static int compareLimbsSorted(final long[] a, final long[] b) {
  //   int diffLen = a.length - b.length;
  //   int cmp = Arrays.mismatch(a, 0, diffLen, ZERO_LONGS, 0, diffLen);
  //   int i = Arrays.mismatch(a, diffLen, a.length, b, 0, b.length);
  //   if (cmp != -1 || cmp >= diffLen) return 1;
  //   else if (i == -1 || i >= b.length) return 0;
  //   else return Long.compareUnsigned(a[i + diffLen], b[i]);
  // }

  // result <- x << shift
  // private static long shiftLeftInto(
  //     final long[] result, final long[] x, final int xOffset, final int shift) {
  //   // Unchecked: result should be initialised with zeroes
  //   // Unchecked: result length should be at least x.length + 1
  //   // Unchecked: 0 <= shift < N_BITS_PER_LIMB
  //   if (shift == 0) {
  //     int xLen = x.length - xOffset;
  //     int resultOffset = result.length - xLen;
  //     System.arraycopy(x, xOffset, result, resultOffset, xLen);
  //     return 0;
  //   }
  //   long carry = 0;
  //   int j = result.length - 1;
  //   for (int i = x.length - 1; i >= xOffset; i--, j--) {
  //     result[j] = (x[i] << shift) | carry;
  //     carry = x[i] >>> (N_BITS_PER_LIMB - shift);
  //   }
  //   return carry;
  // }

  // result <- x >>> shift
  // private static long shiftRightInto(
  //     final long[] result, final long[] x, final int xOffset, final int shift) {
  //   // Unchecked: result length should be at least x.length
  //   // Unchecked: 0 <= shift < N_BITS_PER_LIMB
  //   if (shift == 0) {
  //     int xLen = x.length - xOffset;
  //     int resultOffset = result.length - xLen;
  //     System.arraycopy(x, xOffset, result, resultOffset, xLen);
  //     return 0;
  //   }
  //   long carry = 0;
  //   int j = result.length - x.length + xOffset;
  //   for (int i = xOffset; i < x.length; i++, j++) {
  //     result[j] = (x[i] >>> shift) | carry;
  //     carry = x[i] << (N_BITS_PER_LIMB - shift);
  //   }
  //   return carry;
  // }

  // sum <- a + b, return left over carry
  // private static long addImpl(final long[] sum, final long[] a, final long[] b) {
  //   // Unchecked both length 8
  //   long carry = 0;
  //   int i = sum.length - 1;
  //   carry = adc(a[3], b[3], carry, sum, i--);
  //   carry = adc(a[2], b[2], carry, sum, i--);
  //   carry = adc(a[1], b[1], carry, sum, i--);
  //   carry = adc(a[0], b[0], carry, sum, i--);
  //   return carry;
  // }

  // Strategy: check for overflow for carry
  // private static long adc(
  //     final long a, final long b, final long carryIn, final long[] sum, final int index) {
  //   long s = a + b;
  //   long carryOut = Long.compareUnsigned(s, a) < 0 ? 1 : 0;
  //   s += carryIn;
  //   carryOut |= (s == 0 && carryIn == 1) ? 1 : 0;
  //   sum[index] = s;
  //   return carryOut;
  // }

  // private static long[] addMul(final long[] a, final int aOffset, final long[] b, final int bOffset) {
  //   // Shortest in outer loop, swap if needed
  //   long[] x;
  //   long[] y;
  //   int xOffset;
  //   int yOffset;
  //   if (a.length - aOffset < b.length - bOffset) {
  //     x = b;
  //     xOffset = bOffset;
  //     y = a;
  //     yOffset = aOffset;
  //   } else {
  //     x = a;
  //     xOffset = aOffset;
  //     y = b;
  //     yOffset = bOffset;
  //   }
  //   long[] lhs = new long[x.length + y.length - xOffset - yOffset];
  //
  //   // Main algo
  //   int xLen = x.length - xOffset;
  //   for (int i = y.length - 1; i >= yOffset; i--) {
  //     long carry = 0;
  //     int k = i + xLen - yOffset;
  //     for (int j = x.length - 1; j >= xOffset; j--, k--) {
  //       long p0 = y[i] * x[j];
  //       long p1 = Math.unsignedMultiplyHigh(y[i], x[j]);
  //       p0 += carry;
  //       if (Long.compareUnsigned(p0, carry) < 0) p1++;
  //       lhs[k] += p0;
  //       if (Long.compareUnsigned(lhs[k], p0) < 0) p1++;
  //       carry = p1;
  //     }
  //
  //     // propagate leftover carry
  //     for (; (carry != 0) && (k >= 0); k--) {
  //       lhs[k] += carry;
  //       carry = (Long.compareUnsigned(lhs[k], carry) < 0) ? 1 : 0;
  //     }
  //   }
  //   return lhs;
  // }

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

  //private static DivResult div2by1(final long x1, final long x0, final long y, final long yInv) {
  //  long z1 = x1;
  //  long z0 = x0;

  //  // wrapping umul z1 * yInv
  //  long q0 = z1 * yInv;
  //  long q1 = Math.unsignedMultiplyHigh(z1, yInv);

  //  // wrapping uadd <q1, q0> + <z1, z0> + <1, 0>
  //  long sum = q0 + z0;
  //  long carry = ((q0 & z0) | ((q0 | z0) & ~sum)) >>> 63;
  //  q0 = sum;
  //  q1 += z1 + carry + 1;

  //  z0 -= q1 * y;
  //  if (Long.compareUnsigned(z0, q0) > 0) {
  //    q1 -= 1;
  //    z0 += y;
  //  }
  //  if (Long.compareUnsigned(z0, y) >= 0) {
  //    q1 += 1;
  //    z0 -= y;
  //  }
  //  return new DivResult(q1, z0);
  //}

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

  private static Div2Result div3by2(
      final long x2, final long x1, final long x0,
      final long y1, final long y0, final long yInv) {
    // <x2, x1, x0> divided by <y1, y0>.
    // Requires <x2, x1> < <y1, y0> otherwise quotient overflows.
    long overflow; // carry or borrow
    long res;  // sum or diff
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
    return new Div2Result(q1, z1, z0);
  }

  private UInt256 mod4by1(final long u4, final long y, final long yInv) {
    // Unchecked precondition: y msb is set.
    // if us >= y, overflows, so must substract y first
    // Can happen only with most significant digit ? Not if it was shifted
    long r = u4;
    r = mod2by1(r, u3, y, yInv);
    r = mod2by1(r, u2, y, yInv);
    r = mod2by1(r, u1, y, yInv);
    r = mod2by1(r, u0, y, yInv);
    return new UInt256(0, 0, 0, r);
  }

  private UInt256 mod4by2(final long u4, final long y1, final long y0, final long yInv) {
    // No overflows because u4 < y1
    Div2Result qr;
    long z2 = u4;
    long z1 = u3;
    long z0 = u2;
    qr = div3by2(z2, z1, z0, y1, y0, yInv);
    z2 = qr.r1;
    z1 = qr.r0;
    z0 = u1;
    qr = div3by2(z2, z1, z0, y1, y0, yInv);
    z2 = qr.r1;
    z1 = qr.r0;
    z0 = u0;
    qr = div3by2(z2, z1, z0, y1, y0, yInv);
    return new UInt256(0, 0, qr.r1, qr.r0);
  }

  private UInt256 mod4by3(final long u4, final UInt256 y, final long yInv) {
    long borrow, res, p0, p1;
    long y2 = y.u2;
    long y1 = y.u1;
    long y0 = y.u0;

    // Divide step -> get highest 2 limbs.
    long z3 = u4;
    long z2 = u3;
    long z1 = u2;
    long z0 = u1;
    Div2Result qr = div3by2(z3, z2, z1, y2, y1, yInv);

    // Multiply-subtract: already have highest 2 limbs
    // <u4, u3, u2, u1>  =  <y2, y1, y0> * q
    p0 = y1 * qr.q;
    p1 = Math.unsignedMultiplyHigh(y1, qr.q);
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
      res = z0 + y0 + carry;
      carry = ((z0 & y0) | ((z0 | y0) & ~res)) >>> 63;
      z0 = res;
      res = z1 + y1 + carry;
      carry = ((z1 & y1) | ((z1 | y1) & ~res)) >>> 63;
      z1 = res;
      res = z2 + y2 + carry;
      carry = ((z2 & y2) | ((z2 | y2) & ~res)) >>> 63;
      z2 = res;
      z3 = z3 + carry;
    }

    // Divide step -> get highest 2 limbs.
    borrow = 0;
    z3 = z2;
    z2 = z1;
    z1 = z0;
    z0 = u0;
    qr = div3by2(z3, z2, z1, y2, y1, yInv);

    // Multiply-subtract: already have highest 2 limbs
    // u4, u3, u2, u1  /  0, y2, y1, y0
    p0 = y1 * qr.q;
    p1 = Math.unsignedMultiplyHigh(y1, qr.q);
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
      res = z0 + y0 + carry;
      carry = ((z0 & y0) | ((z0 | y0) & ~res)) >>> 63;
      z0 = res;
      res = z1 + y1 + carry;
      carry = ((z1 & y1) | ((z1 | y1) & ~res)) >>> 63;
      z1 = res;
      res = z2 + y2 + carry;
      // carry = ((z2 & y2) | ((z2 | y2) & ~res)) >>> 63;
      z2 = res;
      // z3 = z3 + carry;
    }
    return new UInt256(0, z2, z1, z0);
  }

  private UInt256 mod4by4(final long u4, final UInt256 y, final long yInv) {
    long borrow, res, p0, p1, p2;
    long y3 = y.u3;
    long y2 = y.u2;
    long y1 = y.u1;
    long y0 = y.u0;

    // Divide step -> get highest 2 limbs.
    long z4 = u4;
    long z1 = u1;
    long z0 = u0;
    Div2Result qr = div3by2(z4, u3, u2, y3, y2, yInv);
    long z3 = qr.r1;
    long z2 = qr.r0;

    // Multiply-subtract: already have highest 2 limbs
    // <z4, z3, z2, z1, z0>  =  <y3, y2, y1, y0> * q
    p0 = y0 * qr.q;
    p1 = Math.unsignedMultiplyHigh(y0, qr.q);
    res = z0 - p0;
    p1 += ((~z0 & p0) | ((~z0 | p0) & res)) >>> 63;
    z0 = res;

    p0 = y1 * qr.q;
    p2 = Math.unsignedMultiplyHigh(y1, qr.q);
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
      res = z0 + y0 + carry;
      carry = ((z0 & y0) | ((z0 | y0) & ~res)) >>> 63;
      z0 = res;
      res = z1 + y1 + carry;
      carry = ((z1 & y1) | ((z1 | y1) & ~res)) >>> 63;
      z1 = res;
      res = z2 + y2 + carry;
      carry = ((z2 & y2) | ((z2 | y2) & ~res)) >>> 63;
      z2 = res;
      res = z3 + y3 + carry;
      // carry = ((z3 & y3) | ((z3 | y3) & ~res)) >>> 63;
      z3 = res;
      // z4 = z4 + carry;
    }
    return new UInt256(z3, z2, z1, z0);
  }

  private UInt256 knuthRemainder4by4(final UInt256 modulus) {
    int shift = Long.numberOfLeadingZeros(modulus.u3);
    UInt256 m = modulus.shiftLeft(shift);
    UInt256 a = shiftLeft(shift);
    long u4 = u3 >>> (N_BITS_PER_LIMB - shift);
    long mInv = reciprocal2(m.u3, m.u2);
    UInt256 r = a.mod4by4(u4, m, mInv);
    return r.shiftRight(shift);
  }

  private UInt256 knuthRemainder4by3(final UInt256 modulus) {
    int shift = Long.numberOfLeadingZeros(modulus.u2);
    UInt256 m = modulus.shiftLeft(shift);
    UInt256 a = shiftLeft(shift);
    long u4 = u3 >>> (N_BITS_PER_LIMB - shift);
    long mInv = reciprocal2(m.u2, m.u1);
    UInt256 r = a.mod4by3(u4, m, mInv);
    return r.shiftRight(shift);
  }

  private UInt256 knuthRemainder4by2(final UInt256 modulus) {
    int shift = Long.numberOfLeadingZeros(modulus.u1);
    UInt256 m = modulus.shiftLeft(shift);
    UInt256 a = shiftLeft(shift);
    long u4 = u3 >>> (N_BITS_PER_LIMB - shift);
    long mInv = reciprocal2(m.u1, m.u0);
    UInt256 r = a.mod4by2(u4, m.u1, m.u0, mInv);
    return r.shiftRight(shift);
  }

  private UInt256 knuthRemainder4by1(final UInt256 modulus) {
    int shift = Long.numberOfLeadingZeros(modulus.u0);
    long m0 = modulus.u0 << shift;
    UInt256 a = shiftLeft(shift);
    long u4 = u3 >>> (N_BITS_PER_LIMB - shift);
    long mInv = reciprocal(m0);
    UInt256 r = a.mod4by1(u4, m0, mInv);
    return r.shiftRight(shift);
  }

  // private record DivResult(long q, long r) {}
  private record Div2Result(long q, long r1, long r0) {}
  // --------------------------------------------------------------------------
  // endregion
}
