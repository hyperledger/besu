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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
  // Length is used to optimise algorithms, skipping leading zeroes.
  // Nonetheless, 256bits are always allocated and initialised to zeroes.

  /** Fixed size in bytes. */
  public static final int BYTESIZE = 32;

  /** Fixed size in bits. */
  public static final int BITSIZE = 256;

  // Fixed number of limbs or digits
  private static final int N_LIMBS = 8;
  // Fixed number of bits per limb.
  private static final int N_BITS_PER_LIMB = 32;
  // Mask for long values
  private static final long MASK_L = 0xFFFFFFFFL;

  // Fixed arrays
  private static final byte[] ZERO_BYTES = new byte[32];
  // We accomodate up to a result of a multiplication
  private static final int[] ZERO_INTS = new int[17];


  private final int[] limbs;
  private final int offset;

  @VisibleForTesting
  int[] limbs() {
    return limbs;
  }

  // --------------------------------------------------------------------------
  // endregion

  /** The constant 0. */
  public static final UInt256 ZERO = new UInt256(new int[] {0, 0, 0, 0, 0, 0, 0, 0}, 0);

  /** The constant All ones */
  public static final UInt256 ALL_ONES =
      new UInt256(
          new int[] {
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFFFFFFF
          },
          N_LIMBS);

  // region Constructors
  // --------------------------------------------------------------------------

  UInt256(final int[] limbs, final int offset) {
    // Unchecked length: assumes limbs have length == N_LIMBS
    this.limbs = limbs;
    this.offset = offset;
  }

  UInt256(final int[] limbs) {
    this(limbs, N_LIMBS);
  }

  /**
   * Instantiates a new UInt256 from byte array.
   *
   * @param bytes raw bytes in BigEndian order.
   * @return Big-endian UInt256 represented by the bytes.
   */
  public static UInt256 fromBytesBE(final byte[] bytes) {
    int[] limbs = new int[N_LIMBS];
    int offset = Arrays.mismatch(bytes, ZERO_BYTES);
    int len = (bytes.length - offset);
    int nFullInts = len >> N_BYTES_PER_LIMB_LOG;
    int nRemaining = len & (N_BYTES_PER_LIMB - 1);

    offset = N_LIMBS - ((bytes.length - offset) >> N_BYTES_PER_LIMB_LOG);
    int i = 7;
    int j = bytes.length - 4;
    switch(nFullInts) {
      // Fall through
      case 8:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 7:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 6:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 5:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 4:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 3:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 2:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
      // Fall through
      case 1:
        limbs[i--] = getIntBE(bytes, j);
        j -= 4;
    }
    if (nRemaining != 0) {
      limbs[i] = getIntBEPartial(bytes, j, nRemaining);
      offset--;
    }
    return new UInt256(limbs, offset);
  }

  // Helper method to read 4 bytes as big-endian int
  private static int getIntBE(final byte[] bytes, final int offset) {
    return ((bytes[offset] & 0xFF) << 24)
        | ((bytes[offset + 1] & 0xFF) << 16)
        | ((bytes[offset + 2] & 0xFF) << 8)
        | (bytes[offset + 3] & 0xFF);
  }

  /**
   * Instantiates a new UInt256 from an int.
   *
   * @param value int value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromInt(final int value) {
    if (value == 0) return ZERO;
    int[] limbs = new int[N_LIMBS];
    limbs[N_LIMBS - 1] = value;
    return new UInt256(limbs, 7);
  }

  /**
   * Instantiates a new UInt256 from a long.
   *
   * @param value long value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromLong(final long value) {
    if (value == 0) return ZERO;
    int[] limbs = new int[N_LIMBS];
    limbs[N_LIMBS - 1] = (int) value;
    limbs[N_LIMBS - 2] = (int) (value >>> 32);
    return new UInt256(limbs, 6);
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
  public static UInt256 fromArray(final int[] arr) {
    int[] limbs = new int[N_LIMBS];
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
    return limbs[N_LIMBS - 1];
  }

  /**
   * Convert to long.
   *
   * @return Value truncated to a long, possibly lossy.
   */
  public long longValue() {
    return (limbs[N_LIMBS - 1] & MASK_L) | ((limbs[N_LIMBS - 2] & MASK_L) << 32);
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBEOld() {
    ByteBuffer buf = ByteBuffer.allocate(BYTESIZE).order(ByteOrder.BIG_ENDIAN);
    for (int i = 0; i < N_LIMBS; i++) {
      buf.putInt(limbs[i]);
    }
    return buf.array();
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBE() {
    byte[] result = new byte[BYTESIZE];
    putIntBE(result, 0, limbs[0]);
    putIntBE(result, 4, limbs[1]);
    putIntBE(result, 8, limbs[2]);
    putIntBE(result, 12, limbs[3]);
    putIntBE(result, 16, limbs[4]);
    putIntBE(result, 20, limbs[5]);
    putIntBE(result, 24, limbs[6]);
    putIntBE(result, 28, limbs[7]);
    return result;
  }

  // Helper method to write 4 bytes from big-endian int
  private static void putIntBE(final byte[] bytes, final int offset, final int value) {
    bytes[offset] = (byte) (value >>> 24);
    bytes[offset + 1] = (byte) (value >>> 16);
    bytes[offset + 2] = (byte) (value >>> 8);
    bytes[offset + 3] = (byte) value;
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
    return (i == -1) ? 0 : Integer.compareUnsigned(a.limbs[i], b.limbs[i]);
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
      h = 31 * h + limbs[i];
    }
    return h;
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
    int[] x = new int[N_LIMBS];
    int[] y = new int[N_LIMBS];
    System.arraycopy(this.limbs, 0, x, 0, N_LIMBS);
    System.arraycopy(modulus.limbs, 0, y, 0, N_LIMBS);
    absInplace(x);
    absInplace(y);
    int[] r = knuthRemainder(x, y);
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
    int[] sum = addImpl(this.limbs, other.limbs);
    int[] rem = knuthRemainder(sum, modulus.limbs);
    return new UInt256(rem, modulus.offset);
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
    System.out.println("MulMod");
    System.out.println(String.format("this Offset: %s, Data: %s", this.offset, Arrays.toString(this.limbs)));
    System.out.println(String.format("other Offset: %s, Data: %s", other.offset, Arrays.toString(other.limbs)));
    System.out.println(String.format("modulus Offset: %s, Data: %s", modulus.offset, Arrays.toString(modulus.limbs)));
    int[] result = addMul(this.limbs, this.offset, other.limbs, other.offset);
    System.out.println(String.format("to_int(%s) * to_int(%s) == to_int(%s)", Arrays.toString(this.limbs), Arrays.toString(other.limbs), Arrays.toString(result)));
    result = knuthRemainder(result, modulus.limbs);
    System.out.println(String.format("to_int(%s) == to_int(%s)", Arrays.toString(modulus.limbs), Arrays.toString(result)));
    return new UInt256(result, modulus.offset);
  }

  /**
   * Bitwise AND operation
   *
   * @param other The UInt256 to AND with this.
   * @return The UInt256 result from the bitwise AND operation
   */
  public UInt256 and(final UInt256 other) {
    int[] result = new int[N_LIMBS];
    result[0] = this.limbs[0] & other.limbs[0];
    result[1] = this.limbs[1] & other.limbs[1];
    result[2] = this.limbs[2] & other.limbs[2];
    result[3] = this.limbs[3] & other.limbs[3];
    result[4] = this.limbs[4] & other.limbs[4];
    result[5] = this.limbs[5] & other.limbs[5];
    result[6] = this.limbs[6] & other.limbs[6];
    result[7] = this.limbs[7] & other.limbs[7];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise XOR operation
   *
   * @param other The UInt256 to XOR with this.
   * @return The UInt256 result from the bitwise XOR operation
   */
  public UInt256 xor(final UInt256 other) {
    int[] result = new int[N_LIMBS];
    result[0] = this.limbs[0] ^ other.limbs[0];
    result[1] = this.limbs[1] ^ other.limbs[1];
    result[2] = this.limbs[2] ^ other.limbs[2];
    result[3] = this.limbs[3] ^ other.limbs[3];
    result[4] = this.limbs[4] ^ other.limbs[4];
    result[5] = this.limbs[5] ^ other.limbs[5];
    result[6] = this.limbs[6] ^ other.limbs[6];
    result[7] = this.limbs[7] ^ other.limbs[7];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise OR operation
   *
   * @param other The UInt256 to OR with this.
   * @return The UInt256 result from the bitwise OR operation
   */
  public UInt256 or(final UInt256 other) {
    int[] result = new int[N_LIMBS];
    result[0] = this.limbs[0] | other.limbs[0];
    result[1] = this.limbs[1] | other.limbs[1];
    result[2] = this.limbs[2] | other.limbs[2];
    result[3] = this.limbs[3] | other.limbs[3];
    result[4] = this.limbs[4] | other.limbs[4];
    result[5] = this.limbs[5] | other.limbs[5];
    result[6] = this.limbs[6] | other.limbs[6];
    result[7] = this.limbs[7] | other.limbs[7];
    return new UInt256(result, N_LIMBS);
  }

  /**
   * Bitwise NOT operation
   *
   * @return The UInt256 result from the bitwise NOT operation
   */
  public UInt256 not() {
    int[] result = new int[N_LIMBS];
    result[0] = ~this.limbs[0];
    result[1] = ~this.limbs[1];
    result[2] = ~this.limbs[2];
    result[3] = ~this.limbs[3];
    result[4] = ~this.limbs[4];
    result[5] = ~this.limbs[5];
    result[6] = ~this.limbs[6];
    result[7] = ~this.limbs[7];
    return new UInt256(result, N_LIMBS);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Support (private) Algorithms
  // --------------------------------------------------------------------------

  // Effective length of a big-endian int array: leading zeroes are ignored 
  private static int effectiveLength(final int[] x) {
    // Unchecked : x.length <= N_LIMBS
    int offset = Arrays.mismatch(x, ZERO_INTS);
    return (offset != -1 || offset != x.length) ? x.length - offset : 0;
  }

  // private static int numberOfLeadingZeroLimbs(final int[] x) {
  //   // Unchecked : x.length <= N_LIMBS
  //   int offset = Arrays.mismatch(x, ZERO.limbs);
  //   return (offset == -1) ? x.length : offset;
  // }

  // private static int numberOfLeadingZeroBits(final int[] x, final int offset, final int length) {
  //   // Unchecked : xLen <= x.length, xLen <= N_LIMBS
  //   int i = Arrays.mismatch(x, offset, length, ZERO.limbs, 0, N_LIMBS);
  //   return N_BITS_PER_LIMB * i + Integer.numberOfLeadingZeros(x[offset + i]);
  // }

  // Comparing two int subarrays as big-endian multi-precision integers.
  private static int compareLimbs(final int[] a, final int[] b) {
    if (a.length >= b.length) {
      int diffLen = a.length - b.length;
      int cmp = Arrays.mismatch(a, 0, diffLen, ZERO_INTS, 0, diffLen);
      if (cmp != -1) return 1;
      int i = Arrays.mismatch(a, diffLen, a.length, b, 0, b.length);
      return (i == -1) ? 0 : Integer.compareUnsigned(a[i + diffLen], b[i]);
    } else {
      int diffLen = b.length - a.length;
      int cmp = Arrays.mismatch(b, 0, diffLen, ZERO_INTS, 0, diffLen);
      if (cmp != -1) return -1;
      int i = Arrays.mismatch(a, 0, a.length, b, diffLen, b.length);
      return (i == -1) ? 0 : Integer.compareUnsigned(a[i], b[i + diffLen]);
    }
  }

  // Does two-complements represent a negative number: i.e. is leading bit set ?
  private static boolean isNeg(final int[] x) {
    return x[0] < 0;
  }

  // Negate in two-complements representation: bitwise NOT + 1
  // Inplace: modifies input x.
  private static void negate(final int[] x) {
    int carry = 1;
    for (int i = x.length - 1; i >= 0; i--) {
      x[i] = ~x[i] + carry;
      carry = (x[i] == 0 && carry == 1) ? 1 : 0;
    }
  }

  // Replaces x with its absolute value in two-complements representation
  private static void absInplace(final int[] x) {
    if (isNeg(x)) negate(x);
  }

  private static int shiftLeftInto(
      final int[] result, final int[] x, final int xOffset, final int shift) {
    // Unchecked: result should be initialised with zeroes
    // Unchecked: result length should be at least x.length + 1
    // Unchecked: 0 <= shift < N_BITS_PER_LIMB
    if (shift == 0) {
      int xLen =  x.length - xOffset;
      int resultOffset = result.length - xLen;
      System.arraycopy(x, xOffset, result, resultOffset, xLen);
      return 0;
    }
    int carry = 0;
    int j = result.length - 1;
    for (int i = x.length - 1; i >= xOffset; i--, j--) {
      result[j] = (x[i] << shift) | carry;
      carry = x[i] >>> (N_BITS_PER_LIMB - shift);
    }
    return carry;
  }

  private static int shiftRightInto(
      final int[] result, final int[] x, final int xOffset, final int shift) {
    // Unchecked: result length should be at least x.length
    // Unchecked: 0 <= shift < N_BITS_PER_LIMB
    if (shift == 0) {
      int xLen =  x.length - xOffset;
      int resultOffset = result.length - xLen;
      System.arraycopy(x, xOffset, result, resultOffset, xLen);
      return 0;
    }
    int carry = 0;
    int j = result.length - x.length + xOffset;
    for (int i = xOffset; i < x.length; i++, j++) {
      result[j] = (x[i] >>> shift) | carry;
      carry = x[i] << (N_BITS_PER_LIMB - shift);
    }
    return carry;
  }

  private static int[] addImpl(final int[] x, final int[] y) {
    // Unchecked: result.length > N_LIMBS
    // Unchecked: x.length == y.length == N_LIMBS
    // Unchecked: N_LIMBS == 8
    int[] sum = new int[9];
    long carry = 0;
    carry = adc(sum, x[7], y[7], carry, 8);
    carry = adc(sum, x[6], y[6], carry, 7);
    carry = adc(sum, x[5], y[5], carry, 6);
    carry = adc(sum, x[4], y[4], carry, 5);
    carry = adc(sum, x[3], y[3], carry, 4);
    carry = adc(sum, x[2], y[2], carry, 3);
    carry = adc(sum, x[1], y[1], carry, 2);
    carry = adc(sum, x[0], y[0], carry, 1);
    sum[0] = (int) carry;
    return sum;
  }

  private static long adc(final int[] sum, final int a, final int b, final long carry, final int index) {
    long aL = a & MASK_L;
    long bL = b & MASK_L;
    long s = aL + bL + carry;
    sum[index] = (int) s;
    return s >>> N_BITS_PER_LIMB;
  }

  private static int[] addMul(final int[] a, final int aOffset, final int[] b, final int bOffset) {
    // Shortest in outer loop, swap if needed
    int[] x;
    int[] y;
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
    int[] lhs = new int[a.length + b.length - xOffset - yOffset + 1];

    // Main algo
    int xLen = x.length - xOffset;
    for (int i = y.length - 1; i >= yOffset; i--) {
      long carry = 0;
      long yi = y[i] & MASK_L;

      int k = i + xLen - yOffset + 1;
      for (int j = x.length - 1; j >= xOffset; j--, k--) {
        long prod = yi * (x[j] & MASK_L);
        long sum = (lhs[k] & MASK_L) + prod + carry;
        lhs[k] = (int) sum;
        carry = sum >>> N_BITS_PER_LIMB;
      }

      // propagate leftover carry
      while (carry != 0 && k >= 0) {
        long sum = (lhs[k] & MASK_L) + carry;
        lhs[k] = (int) sum;
        carry = sum >>> 32;
        k--;
      }
    }
    return lhs;
  }

  private static int[] knuthRemainder(final int[] dividend, final int[] modulus) {
    // Unchecked: modulus is non Zero and non One.
    int[] result = new int[N_LIMBS];
    int modLen = effectiveLength(modulus);
    int divLen = effectiveLength(dividend);

    // Shortcut: if dividend < modulus or dividend == modulus
    System.out.println(String.format("dividend: %s", Arrays.toString(dividend)));
    System.out.println(String.format("modulus: %s", Arrays.toString(modulus)));
    int cmp = compareLimbs(dividend, modulus);
    if (cmp < 0) {
      System.arraycopy(dividend, N_LIMBS - divLen, result, 0, divLen);
      return result;
    } else if (cmp == 0) {
      return result;
    }

    // Shortcut: if modulus has a single limb
    if (modLen == 1) {
      if (divLen == 1) {
        result[N_LIMBS - 1] = Integer.remainderUnsigned(dividend[dividend.length - 1], modulus[modulus.length - 1]);
        return result;
      }
      long d = modulus[modulus.length - 1] & MASK_L;
      long rem = 0;
      // Process from most significant limb downwards
      for (int i = dividend.length - divLen; i < dividend.length; i++) {
        long cur = (rem << 32) | (dividend[i] & MASK_L);
        rem = Long.remainderUnsigned(cur, d);
      }
      result[N_LIMBS - 1] = (int) rem;
      result[N_LIMBS - 2] = (int) (rem >>> 32);
      return result;
    }

    int shift = Integer.numberOfLeadingZeros(modulus[modulus.length - modLen]);
    // Normalize
    System.out.println(String.format("dividend: %s", Arrays.toString(dividend)));
    System.out.println(String.format("modulus: %s", Arrays.toString(modulus)));
    int[] vLimbs = new int[modLen];
    shiftLeftInto(vLimbs, modulus, modulus.length - modLen, shift);
    int[] uLimbs = new int[divLen + 1];
    uLimbs[0] = shiftLeftInto(uLimbs, dividend, dividend.length - divLen, shift);
    int diffLen = divLen - modLen + 1;
    System.out.println(String.format("uLimbs << %s: %s", shift, Arrays.toString(uLimbs)));
    System.out.println(String.format("vLimbs << %s: %s", shift, Arrays.toString(vLimbs)));
    System.out.println(String.format("DiffLen: %s", diffLen));

    long[] vLimbsAsLong = new long[modLen];
    for (int i = 0; i < modLen; i++) {
      vLimbsAsLong[i] = vLimbs[i] & MASK_L;
    }

    // Main division loop
    long vn1 = vLimbsAsLong[0];
    long vn2 = vLimbsAsLong[1];
    for (int j = 1; j < diffLen + 1; j++) {
      long ujn = (uLimbs[j - 1] & MASK_L);
      long ujn1 = (uLimbs[j] & MASK_L);
      long ujn2 = (uLimbs[j + 1] & MASK_L);

      long dividendPart = (ujn << N_BITS_PER_LIMB) | ujn1;
      // Check that no need for Unsigned version of divrem.
      long qhat = Long.divideUnsigned(dividendPart, vn1);
      long rhat = Long.remainderUnsigned(dividendPart, vn1);

      System.out.println(String.format("Qhat: %s", qhat));
      while (qhat == 0x1_0000_0000L || Long.compareUnsigned(qhat * vn2, (rhat << N_BITS_PER_LIMB) | ujn2) > 0) {
        qhat--;
        rhat += vn1;
        if (rhat >= 0x1_0000_0000L) break;
      }
      System.out.println(String.format("Adj-Qhat: %s", qhat));

      // Multiply-subtract qhat*v from u slice
      long borrow = 0;
      for (int i = modLen - 1; i >= 0; i--) {
        long prod = vLimbsAsLong[i] * qhat;
        long sub = (uLimbs[i + j] & MASK_L) - (prod & MASK_L) - borrow;
        uLimbs[i + j] = (int) sub;
        borrow = (prod >>> N_BITS_PER_LIMB) - (sub >> N_BITS_PER_LIMB);
      }
      long sub = (uLimbs[j - 1] & MASK_L) - borrow;
      uLimbs[j - 1] = (int) sub;

      System.out.println(String.format("MulSub uLimbs: %s", Arrays.toString(uLimbs)));
      if (sub < 0) {
        // Add back
        long carry = 0;
        for (int i = modLen - 1; i >= 0; i--) {
          long sum = (uLimbs[i + j] & MASK_L) + vLimbsAsLong[i] + carry;
          uLimbs[i + j] = (int) sum;
          carry = sum >>> N_BITS_PER_LIMB;
        }
        uLimbs[j - 1] = (int) (uLimbs[j - 1] + carry);
        System.out.println(String.format("Adding back uLimbs: %s", Arrays.toString(uLimbs)));
      }
    }
    // Unnormalize remainder
    shiftRightInto(result, uLimbs, diffLen, shift);
    System.out.println(String.format("Results: %s", Arrays.toString(result)));
    return result;
  }
  // --------------------------------------------------------------------------
  // endregion
}
