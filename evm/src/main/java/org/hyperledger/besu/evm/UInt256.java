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


import java.util.Arrays;

/**
 * 256-bits wide unsigned integer class.
 *
 * <p>This class is an optimised version of BigInteger for fixed width 256-bits integers.
 */
public final class UInt256 {
  //region Internals
  //--------------------------------------------------------------------------
  // UInt256 is a big-endian up to 256-bits integer.
  // Internally, it is represented with int/long limbs in little-endian order.
  // Wraps a view over limbs array from 0..length.
  // Internally limbs are little-endian and can perhaps have more elements than length.
  private final int[] limbs;
  private final int length;

  // Maximum number of significant limbs
  private static final int N_LIMBS = 8;
  // Mask for long values
  private static final long MASK_L = 0xFFFFFFFFL;

  // Getters for testing
  int length() {
    return length;
  }

  int[] limbs() {
    return limbs;
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Preallocating Small Integers
  //--------------------------------------------------------------------------
  private static final int nSmallInts = 256;
  private static final UInt256[] smallInts = new UInt256[nSmallInts];

  static {
    smallInts[0] = new UInt256(new int[] {});
    for (int i = 1; i < nSmallInts; i++) {
      smallInts[i] = new UInt256(new int[] {i});
    }
  }

  /** The constant 0. */
  public static final UInt256 ZERO = smallInts[0];

  /** The constant 1. */
  public static final UInt256 ONE = smallInts[1];

  /** The constant 2. */
  public static final UInt256 TWO = smallInts[2];

  /** The constant 10. */
  public static final UInt256 TEN = smallInts[10];

  /** The constant 16. */
  public static final UInt256 SIXTEEN = smallInts[16];
  //--------------------------------------------------------------------------
  //endregion

  //region Constructors
  //--------------------------------------------------------------------------

  UInt256(final int[] limbs, final int length) {
    this.limbs = limbs;
    this.length = length;
    // Unchecked length: assumes length is properly set.
  }

  public UInt256(final int[] limbs) {
    int i = Math.min(limbs.length, N_LIMBS) - 1;
    while ((i >= 0) && (limbs[i] == 0)) i--;
    this.limbs = limbs;
    this.length = i + 1;
  }

  /**
   * Instantiates a new UInt256 from byte[].
   *
   * @param bytes raw bytes in BigEndian order.
   * @return Big-endian UInt256 represented by the bytes.
   */
  public static UInt256 fromBytesBE(final byte[] bytes) {
    int offset = 0;
    while ((offset < bytes.length) && (bytes[offset] == 0x00)) ++offset;
    int nBytes = bytes.length - offset;
    if (nBytes == 0) return ZERO;
    int len = (nBytes + 3) / 4;
    int[] limbs = new int[len];
    // int[] limbs = new int[N_LIMBS];

    int i;
    int base;
    // Up to most significant limb take 4 bytes.
    for (i = 0, base = bytes.length - 4; i < len - 1; ++i, base = base - 4) {
      limbs[i] =
          (bytes[base] << 24)
              | ((bytes[base + 1] & 0xFF) << 16)
              | ((bytes[base + 2] & 0xFF) << 8)
              | ((bytes[base + 3] & 0xFF));
    }
    // Last effective limb
    limbs[i] =
        switch (nBytes - i * 4) {
          case 1 -> ((bytes[offset] & 0xFF));
          case 2 -> (((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF));
          case 3 ->
              (((bytes[offset] & 0xFF) << 16)
                  | ((bytes[offset + 1] & 0xFF) << 8)
                  | (bytes[offset + 2] & 0xFF));
          case 4 ->
              ((bytes[offset] << 24)
                  | ((bytes[offset + 1] & 0xFF) << 16)
                  | ((bytes[offset + 2] & 0xFF) << 8)
                  | (bytes[offset + 3] & 0xFF));
          default -> throw new IllegalStateException("Unexpected value");
        };
    return new UInt256(limbs, len);
  }

  /**
   * Instantiates a new UInt256 from an int.
   *
   * @param value int value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromInt(final int value) {
    if (0 <= value && value < nSmallInts) return smallInts[value];
    return new UInt256(new int[] {value}, 1);
  }

  /**
   * Instantiates a new UInt256 from a long.
   *
   * @param value long value to convert to UInt256.
   * @return The UInt256 equivalent of value.
   */
  public static UInt256 fromLong(final long value) {
    if (0 <= value && value < nSmallInts) return smallInts[(int) value];
    return new UInt256(new int[] {(int) value, (int) (value >>> 32)});
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Conversions
  //--------------------------------------------------------------------------

  /**
   * Convert to int.
   *
   * @return Value truncated to an int, possibly lossy.
   */
  public int intValue() {
    return (limbs.length == 0 ? 0 : limbs[0]);
  }

  /**
   * Convert to long.
   *
   * @return Value truncated to a long, possibly lossy.
   */
  public long longValue() {
    switch (length) {
      case 0 -> {
        return 0L;
      }
      case 1 -> {
        return (limbs[0] & MASK_L);
      }
      default -> {
        return (limbs[0] & MASK_L) | ((limbs[1] & MASK_L) << 32);
      }
    }
  }

  /**
   * Convert to BigEndian byte array.
   *
   * @return Big-endian ordered bytes for this UInt256 value.
   */
  public byte[] toBytesBE() {
    byte[] out = new byte[32];
    for (int i = 0, offset = 28; i < length; i++, offset -= 4) {
      int v = limbs[i];
      out[offset] = (byte) (v >>> 24);
      out[offset + 1] = (byte) (v >>> 16);
      out[offset + 2] = (byte) (v >>> 8);
      out[offset + 3] = (byte) v;
    }
    return out;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("0x");
    byte[] out = new byte[length * 4];
    for (int i = 0, offset = 4 * (length - 1); i < length; i++, offset -= 4) {
      int v = limbs[i];
      out[offset] = (byte) (v >>> 24);
      out[offset + 1] = (byte) (v >>> 16);
      out[offset + 2] = (byte) (v >>> 8);
      out[offset + 3] = (byte) v;
    }
    for (byte b : out) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Comparisons
  //--------------------------------------------------------------------------

  /**
   * Is the value 0 ?
   *
   * @return true if this UInt256 value is 0.
   */
  public boolean isZero() {
    if (length == 0) return true;
    for (int i = 0; i < length; i++) {
      if (limbs[i] != 0) return false;
    }
    return true;
  }

  /**
   * Compares two UInt256.
   *
   * @param a left UInt256
   * @param b right UInt256
   * @return 0 if a == b, negative if a &lt; b and positive if a &gt; b.
   */
  public static int compare(final UInt256 a, final UInt256 b) {
    int comp = Integer.compare(a.length, b.length);
    if (comp != 0) return comp;
    for (int i = a.length - 1; i >= 0; i--) {
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

    // Compare lengths after trimming leading zero limbs
    int cmp = UInt256.compare(this, other);
    return cmp == 0;
  }

  @Override
  public int hashCode() {
    int h = 1;
    for (int i = 0; i < length; i++) {
      h = 31 * h + limbs[i];
    }
    return h;
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Bitwise Operations
  //--------------------------------------------------------------------------

  /**
   * Shifts value to the left.
   *
   * @param shift number of bits to shift. If negative, shift right instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftLeft(final int shift) {
    if (shift >= length * 32) return ZERO;
    if (shift < 0) return shiftRight(-shift);
    if (shift == 0 || isZero()) return this;
    int nDiffBits = shift - numberOfLeadingZeros(this.limbs, this.length);
    int size = this.length + (nDiffBits + 31) / 32;
    int[] shifted = new int[size];
    shiftLeftInto(shifted, this.limbs, shift);
    return new UInt256(shifted, size);
  }

  /**
   * Shifts value to the right.
   *
   * @param shift number of bits to shift. If negative, shift left instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftRight(final int shift) {
    if (shift < 0) return shiftLeft(-shift);
    if (isZero()) return ZERO;
    int[] shifted = new int[this.length];
    shiftRightInto(shifted, this.limbs, shift);
    return new UInt256(shifted);
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Arithmetic Operations
  //--------------------------------------------------------------------------

  /**
   * Addition (modulo 2**256).
   *
   * @param other The integer to add.
   * @return The sum of this with other.
   */
  public UInt256 add(final UInt256 other) {
    if (other.isZero()) return this;
    if (this.isZero()) return other;
    return new UInt256(addWithCarry(this.limbs, other.limbs));
  }

  /**
   * Multiplication (modulo 2**256).
   *
   * @param other The integer to add.
   * @return The sum of this with other.
   */
  public UInt256 mul(final UInt256 other) {
    if (this.isZero() || other.isZero()) return ZERO;
    int[] result = new int[this.length + other.length + 1];
    addMul(result, this.limbs, other.limbs);
    return new UInt256(result);
  }

  /**
   * Reduce modulo modulus.
   *
   * @param modulus The modulus of the reduction
   * @return The remainder modulo {@code modulus}.
   */
  public UInt256 mod(final UInt256 modulus) {
    int cmp = compare(this, modulus);
    if (cmp < 0) return this;
    if (cmp == 0 || modulus.isZero() || this.isZero()) return ZERO;
    return new UInt256(knuthRemainder(this.limbs, modulus.limbs));
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
    if (this.isZero()) return other.mod(modulus);
    if (other.isZero()) return this.mod(modulus);
    int[] sum = addWithCarry(this.limbs, other.limbs);
    int[] rem = knuthRemainder(sum, modulus.limbs);
    return new UInt256(rem);
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
    int[] result = new int[this.length + other.length + 1];
    addMul(result, this.limbs, other.limbs);
    result = knuthRemainder(result, modulus.limbs);
    return new UInt256(result);
  }
  //--------------------------------------------------------------------------
  //endregion

  //region Support (private) Algorithms
  //--------------------------------------------------------------------------
  private static int numberOfLeadingZeros(final int[] x, final int limit) {
    int leadingIndex = limit - 1;
    while ( (leadingIndex >= 0) && (x[leadingIndex] == 0) ) leadingIndex--;
    return 32 * (limit - leadingIndex - 1) + Integer.numberOfLeadingZeros(x[leadingIndex]);
  }

  private static int numberOfLeadingZeros(final int[] x) {
    return numberOfLeadingZeros(x, x.length);
  }

  private static void shiftLeftInto(final int[] result, final int[] x, final int shift) {
    // Unchecked: result should be initialised with zeroes
    int limbShift = shift / 32;
    int bitShift = shift % 32;
    int nLimbs = Math.min(x.length, result.length - limbShift);
    if (shift > 32 * nLimbs) return;
    if (bitShift == 0) {
      System.arraycopy(x, 0, result, limbShift, nLimbs);
      return;
    }

    int j = limbShift;
    int carry = 0;
    for (int i = 0; i < nLimbs; ++i, ++j) {
      result[j] = (x[i] << bitShift) | carry;
      carry = x[i] >>> (32 - bitShift);
    }
    if (carry != 0) result[j] = carry; // last carry
  }

  private static void shiftRightInto(final int[] result, final int[] x, final int shift) {
    int limbShift = shift / 32;
    int bitShift = shift % 32;
    int nLimbs = Math.min(x.length - limbShift, result.length);

    if (shift > 32 * nLimbs) return;
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

  private static int[] addWithCarry(final int[] x, final int[] y) {
    // Step 1: Add with carry
    int[] a;
    int[] b;
    if (x.length < y.length) {
      a = y;
      b = x;
    } else {
      a = x;
      b = y;
    }
    int maxLen = a.length;
    int minLen = b.length;
    int[] sum = new int[maxLen + 1];
    long carry = 0;
    for (int i = 0; i < minLen; i++) {
      long ai = a[i] & MASK_L;
      long bi = b[i] & MASK_L;
      long s = ai + bi + carry;
      sum[i] = (int) s;
      carry = s >>> 32;
    }
    int icarry = (int) carry;
    for (int i = minLen; i < maxLen; i++) {
      sum[i] = a[i] + icarry;
      icarry = (a[i] != 0 && sum[i] == 0) ? 1 : 0;
    }
    sum[maxLen] = icarry;
    return sum;
  }

  private static void addMul(final int[] lhs, final int[] a, final int[] b) {
    // Shortest in outer loop, swap if needed
    int[] x;
    int[] y;
    if (a.length < b.length) { 
      x = b;
      y = a;
    } else {
      x = a;
      y = b;
    }
    // x: widening int -> long
    long[] xl = new long[x.length];
    for (int i = 0; i < xl.length; i++) {
      xl[i] = x[i] & MASK_L;
    }
    // Main algo
    for (int i = 0; i < y.length; i++) {
      long carry = 0;
      long yi = y[i] & MASK_L;

      int k = i;
      for (int j = 0; j < x.length; j++, k++) {
        long prod = yi * xl[j];
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
  }

  private static int[] knuthRemainder(final int[] dividend, final int[] modulus) {
    int shift = numberOfLeadingZeros(modulus);
    int limbShift = shift / 32;
    int n = modulus.length - limbShift;
    if (n == 0) return new int[0];
    if (n == 1) {
      long d = modulus[0] & MASK_L;
      long rem = 0;
      // Process from most significant limb downwards
      for (int i = dividend.length - 1; i >= 0; i--) {
        long cur = (rem << 32) | (dividend[i] & MASK_L);
        rem = Long.remainderUnsigned(cur, d);
      }
      return (new int[] {(int) rem});
    }
    // Normalize
    int m = dividend.length - n;
    int bitShift = shift % 32;
    int[] vLimbs = new int[n];
    shiftLeftInto(vLimbs, modulus, bitShift);
    int[] uLimbs = new int[dividend.length + 1];
    shiftLeftInto(uLimbs, dividend, bitShift);

    // Main division loop
    long vn1 = vLimbs[n - 1] & MASK_L;
    long vn2 = vLimbs[n - 2] & MASK_L;
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
        long prod = (vLimbs[i] & MASK_L) * qhat;
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
          long sum = (uLimbs[i + j] & MASK_L) + (vLimbs[i] & MASK_L) + carry;
          uLimbs[i + j] = (int) sum;
          carry = sum >>> 32;
        }
        uLimbs[j + n] = (int) (uLimbs[j + n] + carry);
      }
    }
    // Unnormalize remainder
    int[] shifted = new int[n];
    shiftRightInto(shifted, uLimbs, bitShift);
    return shifted;
  }
  //--------------------------------------------------------------------------
  //endregion
}
