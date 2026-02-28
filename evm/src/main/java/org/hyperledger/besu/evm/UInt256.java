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
import java.util.Arrays;

import com.google.common.annotations.VisibleForTesting;

/**
 * 256-bits wide unsigned integer class.
 *
 * <p>This class is an optimised version of BigInteger for fixed width 256-bits integers.
 */
public final class UInt256 {
  // region Internals
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

  // Fixed number of limbs or digits
  private static final int N_LIMBS = 4;
  // Fixed number of bits per limb.
  private static final int N_BITS_PER_LIMB = 64;

  // Arrays of zeros.
  // We accomodate up to a result of a multiplication
  private static final long[] ZERO_LONGS = new long[9];

  private final long[] limbs;
  private final int offset;

  public int length() {
    return limbs.length - offset;
  }

  @VisibleForTesting
  long[] limbs() {
    return limbs;
  }

  // --------------------------------------------------------------------------
  // endregion

  /** The constant 0. */
  public static final UInt256 ZERO = new UInt256(new long[] {0, 0, 0, 0}, 0);

  /** The constant All ones */
  public static final UInt256 ALL_ONES =
      new UInt256(
          new long[] {
            0xFFFFFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFFFL,
            0xFFFFFFFFFFFFFFFFL
          },
          N_LIMBS);

  // region Constructors
  // --------------------------------------------------------------------------

  UInt256(final long[] limbs, final int offset) {
    // Unchecked length: assumes limbs have length == N_LIMBS
    this.limbs = limbs;
    this.offset = offset;
  }

  UInt256(final long[] limbs) {
    this(limbs, 0);
  }

  /**
   * Instantiates a new UInt256 from byte array.
   *
   * @param bytes raw bytes in BigEndian order.
   * @return Big-endian UInt256 represented by the bytes.
   */
  public static UInt256 fromBytesBE(final byte[] bytes) {
    int byteLen = bytes.length;
    if (byteLen == 0) return ZERO;

    long[] limbs = new long[N_LIMBS];

    // Fast path for exactly 32 bytes
    if (byteLen == 32) {
      limbs[3] = getLongBE(bytes, 24);
      limbs[2] = getLongBE(bytes, 16);
      limbs[1] = getLongBE(bytes, 8);
      limbs[0] = getLongBE(bytes, 0);
      return new UInt256(limbs, 0);
    }

    // General path for variable length
    int limbIndex = N_LIMBS;
    int byteIndex = byteLen - 1;

    while (byteIndex >= 0 && limbIndex >= 1) {
      long limb = 0;
      int shift = 0;

      for (int j = 0; j < 8 && byteIndex >= 0; j++, byteIndex--, shift += 8) {
        limb |= (bytes[byteIndex] & 0xFFL) << shift;
      }

      limbs[--limbIndex] = limb;
    }
    return new UInt256(limbs, limbIndex);
  }

  // Helper method to read 4 bytes as big-endian int
  private static long getLongBE(final byte[] bytes, final int offset) {
    return ((bytes[offset] & 0xFFL) << 56)
        | ((bytes[offset + 1] & 0xFFL) << 48)
        | ((bytes[offset + 2] & 0xFFL) << 40)
        | ((bytes[offset + 3] & 0xFFL) << 32)
        | ((bytes[offset + 4] & 0xFFL) << 24)
        | ((bytes[offset + 5] & 0xFFL) << 16)
        | ((bytes[offset + 6] & 0xFFL) << 8)
        | (bytes[offset + 7] & 0xFFL);
  }

  /**
   * Instantiates a new UInt256 from an int.
   *
   * @param value int value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromInt(final int value) {
    if (value == 0) return ZERO;
    long[] limbs = new long[N_LIMBS];
    limbs[N_LIMBS - 1] = (long) value;
    return new UInt256(limbs, 3);
  }

  /**
   * Instantiates a new UInt256 from a long.
   *
   * @param value long value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromLong(final long value) {
    if (value == 0) return ZERO;
    long[] limbs = new long[N_LIMBS];
    limbs[N_LIMBS - 1] = value;
    return new UInt256(limbs, 3);
  }

  /**
   * Instantiates a new UInt256 from an int array.
   *
   * <p>The array is interpreted in little-endian order. It is either padded with 0s or truncated if
   * necessary.
   *
   * @param arr int array of limbs.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromArray(final long[] arr) {
    long[] limbs = new long[N_LIMBS];
    int len = Math.min(N_LIMBS, arr.length);
    int offset = N_LIMBS - len;
    System.arraycopy(arr, 0, limbs, offset, len);
    return new UInt256(limbs, offset);
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
    return (int) limbs[N_LIMBS - 1];
  }

  /**
   * Convert to long.
   *
   * @return Value truncated to a long, possibly lossy.
   */
  public long longValue() {
    return limbs[N_LIMBS - 1];
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBE() {
    byte[] result = new byte[BYTESIZE];
    for (int i = this.offset, j = BYTESIZE - 8 * this.length(); i < this.limbs.length; i++, j += 8) {
      putLongBE(result, j, limbs[i]);
    }
    return result;
  }

  // Helper method to write 8 bytes from big-endian int
  private static void putLongBE(final byte[] bytes, final int offset, final long value) {
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
    return Arrays.mismatch(limbs, ZERO.limbs) == -1;
  }

  /**
   * Compares two UInt256.
   *
   * @param a left UInt256
   * @param b right UInt256
   * @return 0 if a == b, negative if a &lt; b and positive if a &gt; b.
   */
  public static int compare(final UInt256 a, final UInt256 b) {
    int i = Arrays.mismatch(a.limbs, b.limbs);
    return (i == -1) ? 0 : Long.compareUnsigned(a.limbs[i], b.limbs[i]);
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof UInt256)) return false;
    UInt256 other = (UInt256) obj;
    return Arrays.mismatch(this.limbs, other.limbs) == -1;
  }

  @Override
  public int hashCode() {
    int h = 1;
    for (int i = 0; i < N_LIMBS; i++) {
      h = 31 * h + Long.hashCode(limbs[i]);
    }
    return h;
  }

  /**
   * Is the two complements signed representation of this integer negative.
   *
   * @return True if the two complements representation of this integer is negative.
   */
  public boolean isNegative() {
    return isNeg(limbs);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Arithmetic Operations
  // --------------------------------------------------------------------------

  /**
   * Unsigned modulo reduction.
   *
   * @param modulus The modulus of the reduction
   * @return The remainder modulo {@code modulus}.
   */
  public UInt256 mod(final UInt256 modulus) {
    if (this.isZero() || modulus.isZero()) return ZERO;
    return new UInt256(knuthRemainder(this.limbs, modulus.limbs));
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
    if (this.isZero() || modulus.isZero()) return ZERO;
    long[] x = new long[N_LIMBS];
    long[] y = new long[N_LIMBS];
    System.arraycopy(this.limbs, 0, x, 0, N_LIMBS);
    System.arraycopy(modulus.limbs, 0, y, 0, N_LIMBS);
    absInplace(x);
    absInplace(y);
    long[] r = knuthRemainder(x, y);
    if (isNeg(this.limbs)) {
      negate(r);
      return new UInt256(r);
    }
    return new UInt256(r, modulus.offset);
  }

  /**
   * Modular addition.
   *
   * @param other The integer to add to this.
   * @param modulus The modulus of the reduction.
   * @return This integer this + other (mod modulus).
   */
  public UInt256 addMod(final UInt256 other, final UInt256 modulus) {
    if (modulus.isZero()) return ZERO;
    long[] sum = new long[N_LIMBS + 1];
    sum[0] = addImpl(sum, this.limbs, other.limbs);
    long[] rem = knuthRemainder(sum, modulus.limbs);
    return new UInt256(rem, rem.length - modulus.limbs.length + modulus.offset);
  }

  /**
   * Modular multiplication.
   *
   * @param other The integer to add to this.
   * @param modulus The modulus of the reduction.
   * @return This integer this + other (mod modulus).
   */
  public UInt256 mulMod(final UInt256 other, final UInt256 modulus) {
    if (this.isZero() || other.isZero() || modulus.isZero()) return ZERO;
    long[] result = addMul(this.limbs, this.offset, other.limbs, other.offset);
    result = knuthRemainder(result, modulus.limbs);
    UInt256 res = new UInt256(result, Math.max(0, result.length - modulus.length()));
    return res;
  }

  /**
   * Bitwise AND operation
   *
   * @param other The UInt256 to AND with this.
   * @return The UInt256 result from the bitwise AND operation
   */
  public UInt256 and(final UInt256 other) {
    long[] result = new long[N_LIMBS];
    result[0] = this.limbs[0] & other.limbs[0];
    result[1] = this.limbs[1] & other.limbs[1];
    result[2] = this.limbs[2] & other.limbs[2];
    result[3] = this.limbs[3] & other.limbs[3];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise XOR operation
   *
   * @param other The UInt256 to XOR with this.
   * @return The UInt256 result from the bitwise XOR operation
   */
  public UInt256 xor(final UInt256 other) {
    long[] result = new long[N_LIMBS];
    result[0] = this.limbs[0] ^ other.limbs[0];
    result[1] = this.limbs[1] ^ other.limbs[1];
    result[2] = this.limbs[2] ^ other.limbs[2];
    result[3] = this.limbs[3] ^ other.limbs[3];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise OR operation
   *
   * @param other The UInt256 to OR with this.
   * @return The UInt256 result from the bitwise OR operation
   */
  public UInt256 or(final UInt256 other) {
    long[] result = new long[N_LIMBS];
    result[0] = this.limbs[0] | other.limbs[0];
    result[1] = this.limbs[1] | other.limbs[1];
    result[2] = this.limbs[2] | other.limbs[2];
    result[3] = this.limbs[3] | other.limbs[3];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise NOT operation
   *
   * @return The UInt256 result from the bitwise NOT operation
   */
  public UInt256 not() {
    long[] result = new long[N_LIMBS];
    result[0] = ~this.limbs[0];
    result[1] = ~this.limbs[1];
    result[2] = ~this.limbs[2];
    result[3] = ~this.limbs[3];
    return new UInt256(result, N_LIMBS);
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
  private static int effectiveLength(final long[] x) {
    // Unchecked : x.length <= N_LIMBS
    int offset = Arrays.mismatch(x, ZERO_LONGS);
    return (offset != -1 || offset != x.length) ? x.length - offset : 0;
  }

  // Comparing two int subarrays as big-endian multi-precision integers.
  private static int compareLimbs(final long[] a, final long[] b) {
    return (a.length >= b.length) ? compareLimbsSorted(a, b) : -compareLimbsSorted(b, a);
  }

  // Comparing two int subarrays as big-endian multi-precision integers,
  // where a is known to be longer than b.
  private static int compareLimbsSorted(final long[] a, final long[] b) {
    int diffLen = a.length - b.length;
    int cmp = Arrays.mismatch(a, 0, diffLen, ZERO_LONGS, 0, diffLen);
    int i = Arrays.mismatch(a, diffLen, a.length, b, 0, b.length);
    if (cmp != -1 || cmp >= diffLen) return 1;
    else if (i == -1 || i >= b.length) return 0;
    else return Long.compareUnsigned(a[i + diffLen], b[i]);
  }

  // Does two-complements represent a negative number: i.e. is leading bit set ?
  private static boolean isNeg(final long[] x) {
    return x[0] < 0;
  }

  // Negate in two-complements representation: bitwise NOT + 1
  // Inplace: modifies input x.
  private static void negate(final long[] x) {
    long carry = 1;
    for (int i = x.length - 1; i >= 0; i--) {
      x[i] = ~x[i] + carry;
      carry = (x[i] == 0 && carry == 1) ? 1 : 0;
    }
  }

  // Replaces x with its absolute value in two-complements representation
  private static void absInplace(final long[] x) {
    if (isNeg(x)) negate(x);
  }

  // result <- x << shift
  private static long shiftLeftInto(
      final long[] result, final long[] x, final int xOffset, final int shift) {
    // Unchecked: result should be initialised with zeroes
    // Unchecked: result length should be at least x.length + 1
    // Unchecked: 0 <= shift < N_BITS_PER_LIMB
    if (shift == 0) {
      int xLen = x.length - xOffset;
      int resultOffset = result.length - xLen;
      System.arraycopy(x, xOffset, result, resultOffset, xLen);
      return 0;
    }
    long carry = 0;
    int j = result.length - 1;
    for (int i = x.length - 1; i >= xOffset; i--, j--) {
      result[j] = (x[i] << shift) | carry;
      carry = x[i] >>> (N_BITS_PER_LIMB - shift);
    }
    return carry;
  }

  // result <- x >>> shift
  private static long shiftRightInto(
      final long[] result, final long[] x, final int xOffset, final int shift) {
    // Unchecked: result length should be at least x.length
    // Unchecked: 0 <= shift < N_BITS_PER_LIMB
    if (shift == 0) {
      int xLen = x.length - xOffset;
      int resultOffset = result.length - xLen;
      System.arraycopy(x, xOffset, result, resultOffset, xLen);
      return 0;
    }
    long carry = 0;
    int j = result.length - x.length + xOffset;
    for (int i = xOffset; i < x.length; i++, j++) {
      result[j] = (x[i] >>> shift) | carry;
      carry = x[i] << (N_BITS_PER_LIMB - shift);
    }
    return carry;
  }

  // sum <- a + b, return left over carry
  private static long addImpl(final long[] sum, final long[] a, final long[] b) {
    // Unchecked both length 8
    long carry = 0;
    int i = sum.length - 1;
    carry = adc(a[3], b[3], carry, sum, i--);
    carry = adc(a[2], b[2], carry, sum, i--);
    carry = adc(a[1], b[1], carry, sum, i--);
    carry = adc(a[0], b[0], carry, sum, i--);
    return carry;
  }

  // Strategy: check for overflow for carry
  private static long adc(
      final long a, final long b, final long carryIn, final long[] sum, final int index) {
    long s = a + b;
    long carryOut = Long.compareUnsigned(s, a) < 0 ? 1 : 0;
    s += carryIn;
    carryOut |= (s == 0 && carryIn == 1) ? 1 : 0;
    sum[index] = s;
    return carryOut;
  }

  private static long[] addMul(final long[] a, final int aOffset, final long[] b, final int bOffset) {
    // Shortest in outer loop, swap if needed
    long[] x;
    long[] y;
    int xOffset;
    int yOffset;
    if (a.length - aOffset < b.length - bOffset) {
      x = b;
      xOffset = bOffset;
      y = a;
      yOffset = aOffset;
    } else {
      x = a;
      xOffset = aOffset;
      y = b;
      yOffset = bOffset;
    }
    long[] lhs = new long[x.length + y.length - xOffset - yOffset];

    // Main algo
    int xLen = x.length - xOffset;
    for (int i = y.length - 1; i >= yOffset; i--) {
      long carry = 0;
      int k = i + xLen - yOffset;
      for (int j = x.length - 1; j >= xOffset; j--, k--) {
        long p0 = y[i] * x[j];
        long p1 = Math.unsignedMultiplyHigh(y[i], x[j]);
        p0 += carry;
        if (Long.compareUnsigned(p0, carry) < 0) p1++;
        lhs[k] += p0;
        if (Long.compareUnsigned(lhs[k], p0) < 0) p1++;
        carry = p1;
      }

      // propagate leftover carry
      for (; (carry != 0) && (k >= 0); k--) {
        lhs[k] += carry;
        carry = (Long.compareUnsigned(lhs[k], carry) < 0) ? 1 : 0;
      }
    }
    return lhs;
  }

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

  private static long reciprocal(final long x0, final long x1) {
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

  private static long div2by1(final long[] xs, final int xOffset, final long y, final long yInv) {
    // Performs on x a single step of x / y
    long x0 = xs[xOffset + 1];
    long x1 = xs[xOffset];

    // wrapping umul x1 * yInv
    long q0 = x1 * yInv;
    long q1 = Math.unsignedMultiplyHigh(x1, yInv);

    // wrapping uadd <q1, q0> + <x1, x0> + <1, 0>
    long sum = q0 + x0;
    long carry = ((q0 & x0) | ((q0 | x0) & ~sum)) >>> 63;
    q0 = sum;
    q1 += x1 + carry + 1;

    xs[xOffset + 1] -= q1 * y;

    if (Long.compareUnsigned(xs[xOffset + 1], q0) > 0) {
      q1 -= 1;
      xs[xOffset + 1] += y;
    }

    if (Long.compareUnsigned(xs[xOffset + 1], y) >= 0) {
      q1 += 1;
      xs[xOffset + 1] -= y;
    }
    xs[xOffset] = 0;
    return q1;
  }

  private static long div3by2(
      final long[] xs, final int xOffset, final long y0, final long y1, final long yInv) {
    // <x2, x1, x0> divided by <y1, y0>.
    // Works inplace on x, modifying it to hold the remainder.
    // Returns the quotient q.
    // Requires <x2, x1> < <y1, y0> otherwise quotient overflows.
    long overflow; // carry or borrow
    long res;  // sum or diff
    long x0 = xs[xOffset + 2];
    long x1 = xs[xOffset + 1];
    long x2 = xs[xOffset];

    // <q1, q0> = x2 * yInv + <x2, x1>
    long q0 = x2 * yInv;
    long q1 = Math.unsignedMultiplyHigh(x2, yInv);
    res = q0 + x1;
    overflow = ((q0 & x1) | ((q0 | x1) & ~res)) >>> 63;
    q0 = res;
    q1 = q1 + x2 + overflow;

    // r1 <- x1 - q1 * y1 mod B
    xs[xOffset + 1] -= q1 * y1;

    // wrapping sub <r1, x0> − q1*y0 − <y1, y0>
    long t0 = q1 * y0;
    long t1 = Math.unsignedMultiplyHigh(q1, y0);

    res = x0 - t0;
    overflow = ((~x0 & t0) | ((~x0 | t0) & res)) >>> 63;
    xs[xOffset + 2] = res;
    xs[xOffset + 1] -= (t1 + overflow);

    res = xs[xOffset + 2] - y0;
    overflow = ((~xs[xOffset + 2] & y0) | ((~xs[xOffset + 2] | y0) & res)) >>> 63;
    xs[xOffset + 2] = res;
    xs[xOffset + 1] -= (y1 + overflow);

    // Adjustments
    q1 += 1;
    if (Long.compareUnsigned(xs[xOffset + 1], q0) >= 0) {
      q1 -= 1;
      res = xs[xOffset + 2] + y0;
      overflow = ((xs[xOffset + 2] & y0) | ((xs[xOffset + 2] | y0) & ~res)) >>> 63;
      xs[xOffset + 2] = res;
      xs[xOffset + 1] += y1 + overflow;
    }

    int cmp = Long.compareUnsigned(xs[xOffset + 1], y1);
    if ((cmp > 0) || ((cmp == 0) && (Long.compareUnsigned(xs[xOffset + 2], y0) >= 0))) {
      q1 += 1;
      res = xs[xOffset + 2] - y0;
      overflow = ((~xs[xOffset + 2] & y0) | ((~xs[xOffset + 2] | y0) & res)) >>> 63;
      xs[xOffset + 2] = res;
      xs[xOffset + 1] -= (y1 + overflow);
    }
    xs[xOffset] = 0;
    return q1;
  }

  private static void modNby1(final long[] xs, final int xOffset, final long y, final long yInv) {
    // Unchecked: y > 1 << 63
    // if xs >= y, overflows, so must substract y first
    // Can happen only with most significant digit ?
    for (int i = xOffset; i < xs.length - 1; i++) {
      if (Long.compareUnsigned(xs[i], y) >= 0) xs[i] -= y;
      div2by1(xs, i, y, yInv);
    }
  }

  private static void modNby2(
      final long[] xs, final int xOffset, final long y0, final long y1, final long yInv) {
    // if <x+1, x+0> >= <y1, y0>, overflows, so must substract <y1, y0> first
    for (int i = xOffset; i < xs.length - 2; i++) {
      long x1 = xs[i];
      long x0 = xs[i + 1];
      int cmp = Long.compareUnsigned(x1, y1);
      if ((cmp > 0) || ((cmp == 0) && (Long.compareUnsigned(x0, y0) >= 0))) {
        long borrow = (Long.compareUnsigned(y0, x0) > 0) ? 1 : 0;
        xs[i + 1] -= y0;
        xs[i] -= (y1 + borrow);
      }
      div3by2(xs, i, y0, y1, yInv);
    }
  }

  private static void modNbyM(
      final long[] xs, final int xOffset, final long[] ys, final int yOffset, final long yInv) {
    long res;
    long borrow = 0;
    long y1 = ys[yOffset];
    long y0 = ys[yOffset + 1];
    int yLen = ys.length - yOffset;
    int xLen = xs.length - xOffset;
    for (int j = 0; j < xLen - yLen; j++) {
      // Unlikely overflow 3x2 case: x[..2] == y
      if (((y1 ^ xs[j]) | (y0 ^ xs[j + 1])) == 0) {
        // q = <1, 0> in this case, so multiply is trivial.
        // Still need to substract (and add back if needed).
        int i = j + yLen - 1;
        for (int k = ys.length - 1; k >= yOffset; i--, k--) {
          res = xs[i] - ys[k] - borrow;
          borrow = ((~xs[i] & ys[k]) | ((~xs[i] | ys[k]) & res)) >>> 63;
          xs[i] = res;
        }
        // xs[i] -= borrow;
        // borrow = (xs[i] >>> 63) & 1L;
      } else {
        long q = div3by2(xs, j, y0, y1, yInv);
        long res0;
        long prodHigh = 0;

        // Multiply-subtract: already have highest 2 limbs
        int i = j + yLen;
        for (int k = ys.length - 1; k >= yOffset + 2; k--, i--) {
          long p0 = ys[k] * q;
          long p1 = Math.unsignedMultiplyHigh(ys[k], q);
          res0 = xs[i] - p0 - borrow;
          p1 += ((~xs[i] & p0) | ((~xs[i] | p0) & res0)) >>> 63;  // p1 < b - 1 so can add 0 or 1
          res = res0 - prodHigh;
          borrow = ((~res0 & prodHigh) | ((~res0 | prodHigh) & res)) >>> 63;
          xs[i] = res;
          prodHigh = p1;
        }
        // Propagate overflows (borrows)
        res = xs[i] - prodHigh - borrow;
        borrow = ((~xs[i] & prodHigh) | ((~xs[i] | prodHigh) & res)) >>> 63;
        xs[i--] = res;

        res = xs[i] - borrow;
        borrow = (((borrow ^ 1L) | xs[i]) == 0) ? 1 : 0;
        xs[i--] = res;

        xs[i] -= borrow;
        borrow = (xs[i] >>> 63) & 1;
      }

      if (borrow != 0) { // unlikely
        // Add back
        long carry = 0;
        int i = j + yLen;
        for (int k = ys.length - 1; k >= yOffset; k--, i--) {
          res = xs[i] + ys[k] + carry;
          carry = ((xs[i] & ys[k]) | ((xs[i] | ys[k]) & ~res)) >>> 63;
          xs[i] = res;
        }
        for (; (carry != 0) && (i >= j); i--) {
          xs[i] += carry;
          carry = (xs[i] == 0L) ? 1L : 0L;
        }
      }
      borrow = 0;
    }
  }

  private static long[] knuthRemainder(final long[] dividend, final long[] modulus) {
    long[] result = new long[N_LIMBS];
    int divLen = effectiveLength(dividend);
    int modLen = effectiveLength(modulus);

    // Shortcuts
    if (modLen == 0) return result;
    if (divLen == 1) {
      if (modLen == 1) {
        result[result.length - 1] = Long.remainderUnsigned(dividend[dividend.length - 1], modulus[modulus.length - 1]);
        return result;
      } else {
        return dividend;
      }
    }
    // Shortcut: if dividend < modulus or dividend == modulus
    int cmp = compareLimbs(dividend, modulus);
    if (cmp < 0) {
      // System.arraycopy(dividend, dividend.length - divLen, result, N_LIMBS - divLen, divLen);
      // return result;
      return dividend;
    } else if (cmp == 0) {
      return result;
    }

    // Perform Division
    // -- Normalise
    int modOffset = modulus.length - modLen;
    int shift = Long.numberOfLeadingZeros(modulus[modOffset]);
    long[] vLimbs = new long[modLen];
    shiftLeftInto(vLimbs, modulus, modOffset, shift);
    long[] uLimbs = new long[divLen + 1];
    uLimbs[0] = shiftLeftInto(uLimbs, dividend, dividend.length - divLen, shift);
    int diffLen = divLen - modLen + 1;

    // -- Divide
    if (modLen == 1) {
      long inv = reciprocal(vLimbs[0]);
      modNby1(uLimbs, 0, vLimbs[0], inv);
    } else if (modLen == 2) {
      long inv = reciprocal(vLimbs[1], vLimbs[0]);
      modNby2(uLimbs, 0, vLimbs[1], vLimbs[0], inv);
    } else {
      long inv = reciprocal(vLimbs[1], vLimbs[0]);
      modNbyM(uLimbs, 0, vLimbs, 0, inv);
    }
    // -- Unnormalize
    shiftRightInto(result, uLimbs, diffLen, shift);
    return result;
  }
  // --------------------------------------------------------------------------
  // endregion
}
