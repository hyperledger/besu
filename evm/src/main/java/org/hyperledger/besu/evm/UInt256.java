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

/**
 * 256-bits wide unsigned integer class.
 *
 * <p>This class is an optimised version of BigInteger for fixed width 256-bits integers.
 */
public final class UInt256 {
  // region Internals
  // --------------------------------------------------------------------------
  // UInt256 is a big-endian up to 256-bits integer.
  // Internally, it is represented with fixed-size int/long limbs in little-endian order.
  // Length is used to optimise algorithms, skipping leading zeroes.
  // Nonetheless, 256bits are always allocated.

  /** Fixed size in bytes. */
  public static final int BYTESIZE = 32;

  /** Fixed size in bits. */
  public static final int BITSIZE = 256;

  // Fixed number of limbs or digits
  private static final int N_LIMBS = 8;
  // Fixed number of bytes per limb.
  private static final int N_BYTES_PER_LIMB = 4;
  // Fixed number of bits per limb.
  private static final int N_BITS_PER_LIMB = 32;
  // Mask for long values
  private static final long MASK_L = 0xFFFFFFFFL;

  private final int[] limbs;
  private final int length;

  int[] limbs() {
    return limbs;
  }

  // --------------------------------------------------------------------------
  // endregion

  /** The constant 0. */
  public static final UInt256 ZERO = new UInt256(new int[] {0, 0, 0, 0, 0, 0, 0, 0}, 0);

  // region Constructors
  // --------------------------------------------------------------------------

  UInt256(final int[] limbs, final int length) {
    // Unchecked length: assumes limbs have length == N_LIMBS
    this.limbs = limbs;
    this.length = length;
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
    int nLimbs = (bytes.length + N_BYTES_PER_LIMB - 1) / N_BYTES_PER_LIMB;
    int nBytes = nLimbs * N_BYTES_PER_LIMB;
    byte[] padded = new byte[nBytes];
    System.arraycopy(bytes, 0, padded, nBytes - bytes.length, bytes.length);
    ByteBuffer buf = ByteBuffer.wrap(padded).order(ByteOrder.BIG_ENDIAN);
    int[] limbs = new int[N_LIMBS];
    for (int i = nLimbs - 1; i >= 0; i--) {
      limbs[i] = buf.getInt(); // reverse order for little-endian limbs
    }
    return new UInt256(limbs, nLimbs);
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
    limbs[0] = value;
    return new UInt256(limbs, 1);
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
    limbs[0] = (int) value;
    limbs[1] = (int) (value >>> 32);
    return new UInt256(limbs, 2);
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
    System.arraycopy(arr, 0, limbs, 0, len);
    return new UInt256(limbs, len);
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
    return limbs[0];
  }

  /**
   * Convert to long.
   *
   * @return Value truncated to a long, possibly lossy.
   */
  public long longValue() {
    return (limbs[0] & MASK_L) | ((limbs[1] & MASK_L) << 32);
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBE() {
    ByteBuffer buf = ByteBuffer.allocate(BYTESIZE).order(ByteOrder.BIG_ENDIAN);
    for (int i = N_LIMBS - 1; i >= 0; i--) {
      buf.putInt(limbs[i]);
    }
    return buf.array();
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
    return (limbs[0] | limbs[1] | limbs[2] | limbs[3] | limbs[4] | limbs[5] | limbs[6] | limbs[7])
        == 0;
  }

  /**
   * Compares two UInt256.
   *
   * @param a left UInt256
   * @param b right UInt256
   * @return 0 if a == b, negative if a &lt; b and positive if a &gt; b.
   */
  public static int compare(final UInt256 a, final UInt256 b) {
    int comp;
    for (int i = N_LIMBS - 1; i >= 0; i--) {
      comp = Integer.compareUnsigned(a.limbs[i], b.limbs[i]);
      if (comp != 0) return comp;
    }
    return 0;
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof UInt256)) return false;
    UInt256 other = (UInt256) obj;

    int xor =
        (this.limbs[0] ^ other.limbs[0])
            | (this.limbs[1] ^ other.limbs[1])
            | (this.limbs[2] ^ other.limbs[2])
            | (this.limbs[3] ^ other.limbs[3])
            | (this.limbs[4] ^ other.limbs[4])
            | (this.limbs[5] ^ other.limbs[5])
            | (this.limbs[6] ^ other.limbs[6])
            | (this.limbs[7] ^ other.limbs[7]);
    return xor == 0;
  }

  @Override
  public int hashCode() {
    int h = 1;
    for (int i = 0; i < N_LIMBS; i++) {
      h = 31 * h + limbs[i];
    }
    return h;
  }

  /**
   * Is the two complements signed representation of this integer negative.
   *
   * @return True if the two complements representation of this integer is negative.
   */
  public boolean isNegative() {
    return (length == 8) && (isNeg(limbs, 8));
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Bitwise Operations
  // --------------------------------------------------------------------------

  /**
   * Shifts value to the left.
   *
   * @param shift number of bits to shift. If negative, shift right instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftLeft(final int shift) {
    if (shift < 0) return shiftRight(-shift);
    if (shift >= BITSIZE) return ZERO;
    if (shift == 0 || isZero()) return this;
    int[] shifted = new int[N_LIMBS];
    shiftLeftInto(shifted, this.limbs, this.length, shift);
    return new UInt256(shifted);
  }

  /**
   * Shifts value to the right.
   *
   * @param shift number of bits to shift. If negative, shift left instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftRight(final int shift) {
    if (shift < 0) return shiftLeft(-shift);
    if (shift >= length * N_BITS_PER_LIMB) return ZERO;
    if (shift == 0 || isZero()) return this;
    int[] shifted = new int[N_LIMBS];
    shiftRightInto(shifted, this.limbs, this.length, shift);
    return new UInt256(shifted);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Arithmetic Operations
  // --------------------------------------------------------------------------

  /**
   * (Signed) absolute value
   *
   * @return The absolute value of this signed integer.
   */
  public UInt256 abs() {
    int[] newLimbs = Arrays.copyOf(limbs, N_LIMBS);
    absInplace(newLimbs, N_LIMBS);
    return new UInt256(newLimbs);
  }

  /**
   * Addition (modulo 2**256).
   *
   * @param other The integer to add.
   * @return The sum of this with other.
   */
  public UInt256 add(final UInt256 other) {
    if (other.isZero()) return this;
    if (this.isZero()) return other;
    return new UInt256(addWithCarry(this.limbs, this.length, other.limbs, other.length));
  }

  /**
   * Multiplication (modulo 2**256).
   *
   * @param other The integer to add.
   * @return The sum of this with other.
   */
  public UInt256 mul(final UInt256 other) {
    if (this.isZero() || other.isZero()) return ZERO;
    int[] prod = addMul(this.limbs, this.length, other.limbs, other.length);
    int[] result = Arrays.copyOf(prod, N_LIMBS);
    return new UInt256(result);
  }

  /**
   * Unsigned modulo reduction.
   *
   * @param modulus The modulus of the reduction
   * @return The remainder modulo {@code modulus}.
   */
  public UInt256 mod(final UInt256 modulus) {
    if (this.isZero() || modulus.isZero()) return ZERO;
    return new UInt256(knuthRemainder(this.limbs, modulus.limbs), modulus.length);
  }

  /**
   * Signed modulo reduction.
   *
   * @param modulus The modulus of the reduction
   * @return The remainder modulo {@code modulus}.
   */
  public UInt256 signedMod(final UInt256 modulus) {
    if (this.isZero() || modulus.isZero()) return ZERO;
    int[] x = new int[N_LIMBS];
    int[] y = new int[N_LIMBS];
    absInto(x, this.limbs, N_LIMBS);
    absInto(y, modulus.limbs, N_LIMBS);
    int[] r = knuthRemainder(x, y);
    if (isNeg(this.limbs, N_LIMBS)) {
      negate(r, N_LIMBS);
      return new UInt256(r);
    }
    return new UInt256(r, modulus.length);
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
    // if (this.isZero()) return other.mod(modulus);
    // if (other.isZero()) return this.mod(modulus);
    int[] sum = addWithCarry(this.limbs, this.length, other.limbs, other.length);
    int[] rem = knuthRemainder(sum, modulus.limbs);
    return new UInt256(rem, modulus.length);
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
    int[] result = addMul(this.limbs, this.length, other.limbs, other.length);
    result = knuthRemainder(result, modulus.limbs);
    return new UInt256(result, modulus.length);
  }

  // --------------------------------------------------------------------------
  // endregion

  // region Support (private) Algorithms
  // --------------------------------------------------------------------------
  private static int nSetLimbs(final int[] x) {
    int offset = x.length - 1;
    while ((offset >= 0) && (x[offset] == 0)) offset--;
    return offset + 1;
  }

  // private static int nSetLimbs(final int[] x, final int maxLength) {
  //   int offset = maxLength - 1;
  //   while ((offset >= 0) && (x[offset] == 0)) offset--;
  //   return offset + 1;
  // }

  private static int compareLimbs(final int[] a, final int aLen, final int[] b, final int bLen) {
    int cmp;
    if (aLen > bLen) {
      for (int i = aLen - 1; i >= bLen; i--) {
        cmp = Integer.compareUnsigned(a[i], 0);
        if (cmp != 0) return cmp;
      }
    } else if (aLen < bLen) {
      for (int i = bLen - 1; i >= aLen; i--) {
        cmp = Integer.compareUnsigned(0, b[i]);
        if (cmp != 0) return cmp;
      }
    }
    for (int i = Math.min(aLen, bLen) - 1; i >= 0; i--) {
      cmp = Integer.compareUnsigned(a[i], b[i]);
      if (cmp != 0) return cmp;
    }
    return 0;
  }

  private static boolean isNeg(final int[] x, final int xLen) {
    return x[xLen - 1] < 0;
  }

  private static void negate(final int[] x, final int xLen) {
    int carry = 1;
    for (int i = 0; i < xLen; i++) {
      x[i] = ~x[i] + carry;
      carry = (x[i] == 0 && carry == 1) ? 1 : 0;
    }
  }

  private static void absInplace(final int[] x, final int xLen) {
    if (isNeg(x, xLen)) negate(x, xLen);
  }

  private static void absInto(final int[] dst, final int[] src, final int srcLen) {
    System.arraycopy(src, 0, dst, 0, srcLen);
    absInplace(dst, dst.length);
  }

  private static int numberOfLeadingZeros(final int[] x, final int xLen) {
    int leadingIndex = xLen - 1;
    while ((leadingIndex >= 0) && (x[leadingIndex] == 0)) leadingIndex--;
    return 32 * (xLen - leadingIndex - 1) + Integer.numberOfLeadingZeros(x[leadingIndex]);
  }

  private static void shiftLeftInto(
      final int[] result, final int[] x, final int xLen, final int shift) {
    // Unchecked: result should be initialised with zeroes
    // Unchecked: result length should be at least x.length + limbShift
    int limbShift = shift / N_BITS_PER_LIMB;
    int bitShift = shift % N_BITS_PER_LIMB;
    if (limbShift >= xLen) return;
    if (bitShift == 0) {
      System.arraycopy(x, 0, result, limbShift, xLen);
      return;
    }

    int j = limbShift;
    int carry = 0;
    for (int i = 0; i < xLen; ++i, ++j) {
      result[j] = (x[i] << bitShift) | carry;
      carry = x[i] >>> (32 - bitShift);
    }
    if (carry != 0) result[j] = carry; // last carry
  }

  private static void shiftRightInto(
      final int[] result, final int[] x, final int xLen, final int shift) {
    // Unchecked: result length should be at least x.length - limbShift
    int limbShift = shift / 32;
    int bitShift = shift % 32;
    int nLimbs = xLen - limbShift;
    if (nLimbs <= 0) return;

    if (bitShift == 0) {
      System.arraycopy(x, limbShift, result, 0, nLimbs);
      return;
    }

    int carry = 0;
    for (int i = nLimbs - 1 + limbShift, j = nLimbs - 1; j >= 0; i--, j--) {
      int r = (x[i] >>> bitShift) | carry;
      result[j] = r;
      carry = x[i] << (32 - bitShift);
    }
  }

  private static int[] addWithCarry(final int[] x, final int xLen, final int[] y, final int yLen) {
    // Step 1: Add with carry
    int[] a;
    int[] b;
    int aLen;
    int bLen;
    if (xLen < yLen) {
      a = y;
      aLen = yLen;
      b = x;
      bLen = xLen;
    } else {
      a = x;
      aLen = xLen;
      b = y;
      bLen = yLen;
    }
    int[] sum = new int[aLen + 1];
    long carry = 0;
    for (int i = 0; i < bLen; i++) {
      long ai = a[i] & MASK_L;
      long bi = b[i] & MASK_L;
      long s = ai + bi + carry;
      sum[i] = (int) s;
      carry = s >>> 32;
    }
    int icarry = (int) carry;
    for (int i = bLen; i < aLen; i++) {
      sum[i] = a[i] + icarry;
      icarry = (a[i] != 0 && sum[i] == 0) ? 1 : 0;
    }
    sum[aLen] = icarry;
    return sum;
  }

  private static int[] addMul(final int[] a, final int aLen, final int[] b, final int bLen) {
    // Shortest in outer loop, swap if needed
    int[] x;
    int xLen;
    int[] y;
    int yLen;
    if (a.length < b.length) {
      x = b;
      xLen = bLen;
      y = a;
      yLen = aLen;
    } else {
      x = a;
      xLen = aLen;
      y = b;
      yLen = bLen;
    }
    int[] lhs = new int[xLen + yLen + 1];

    // Main algo
    for (int i = 0; i < yLen; i++) {
      long carry = 0;
      long yi = y[i] & MASK_L;

      int k = i;
      for (int j = 0; j < xLen; j++, k++) {
        long prod = yi * (x[j] & MASK_L);
        long sum = (lhs[k] & MASK_L) + prod + carry;
        lhs[k] = (int) sum;
        carry = sum >>> 32;
      }

      // propagate leftover carry
      while (carry != 0 && k < lhs.length) {
        long sum = (lhs[k] & MASK_L) + carry;
        lhs[k] = (int) sum;
        carry = sum >>> 32;
        k++;
      }
    }
    return lhs;
  }

  private static int[] knuthRemainder(final int[] dividend, final int[] modulus) {
    int[] result = new int[N_LIMBS];
    int divLen = nSetLimbs(dividend);
    int modLen = nSetLimbs(modulus);
    int cmp = compareLimbs(dividend, divLen, modulus, modLen);
    if (cmp < 0) {
      System.arraycopy(dividend, 0, result, 0, divLen);
      return result;
    } else if (cmp == 0) {
      return result;
    }

    int shift = numberOfLeadingZeros(modulus, modLen);
    int limbShift = shift / 32;
    int n = modLen - limbShift;
    if (n == 0) return result;
    if (n == 1) {
      if (divLen == 1) {
        result[0] = Integer.remainderUnsigned(dividend[0], modulus[0]);
        return result;
      }
      long d = modulus[0] & MASK_L;
      long rem = 0;
      // Process from most significant limb downwards
      for (int i = divLen - 1; i >= 0; i--) {
        long cur = (rem << 32) | (dividend[i] & MASK_L);
        rem = Long.remainderUnsigned(cur, d);
      }
      result[0] = (int) rem;
      result[1] = (int) (rem >>> 32);
      return result;
    }
    // Normalize
    int m = divLen - n;
    int bitShift = shift % 32;
    int[] vLimbs = new int[n];
    shiftLeftInto(vLimbs, modulus, modLen, shift);
    int[] uLimbs = new int[divLen + 1];
    shiftLeftInto(uLimbs, dividend, divLen, bitShift);

    long[] vLimbsAsLong = new long[n];
    for (int i = 0; i < n; i++) {
      vLimbsAsLong[i] = vLimbs[i] & MASK_L;
    }

    // Main division loop
    long vn1 = vLimbsAsLong[n - 1];
    long vn2 = vLimbsAsLong[n - 2];
    for (int j = m; j >= 0; j--) {
      long ujn = (uLimbs[j + n] & MASK_L);
      long ujn1 = (uLimbs[j + n - 1] & MASK_L);
      long ujn2 = (uLimbs[j + n - 2] & MASK_L);

      long dividendPart = (ujn << 32) | ujn1;
      // Check that no need for Unsigned version of divrem.
      long qhat = Long.divideUnsigned(dividendPart, vn1);
      long rhat = Long.remainderUnsigned(dividendPart, vn1);

      while (qhat == 0x1_0000_0000L || Long.compareUnsigned(qhat * vn2, (rhat << 32) | ujn2) > 0) {
        qhat--;
        rhat += vn1;
        if (rhat >= 0x1_0000_0000L) break;
      }

      // Multiply-subtract qhat*v from u slice
      long borrow = 0;
      for (int i = 0; i < n; i++) {
        long prod = vLimbsAsLong[i] * qhat;
        long sub = (uLimbs[i + j] & MASK_L) - (prod & MASK_L) - borrow;
        uLimbs[i + j] = (int) sub;
        borrow = (prod >>> 32) - (sub >> 32);
      }
      long sub = (uLimbs[j + n] & MASK_L) - borrow;
      uLimbs[j + n] = (int) sub;

      if (sub < 0) {
        // Add back
        long carry = 0;
        for (int i = 0; i < n; i++) {
          long sum = (uLimbs[i + j] & MASK_L) + vLimbsAsLong[i] + carry;
          uLimbs[i + j] = (int) sum;
          carry = sum >>> 32;
        }
        uLimbs[j + n] = (int) (uLimbs[j + n] + carry);
      }
    }
    // Unnormalize remainder
    shiftRightInto(result, uLimbs, n, bitShift);
    return result;
  }
  // --------------------------------------------------------------------------
  // endregion
}
