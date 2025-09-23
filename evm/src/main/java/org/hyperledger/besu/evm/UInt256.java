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
 * <p>This class is an optimised version of BigInteger for fixed width 8byte integers
 */
public final class UInt256 {
  // UInt256 is a big-endian up to 256-bits integer.
  // Internally, it is represented with int/long limbs in little-endian order.
  // Wraps a view over limbs array from 0..length.
  // Internally limbs are little-endian and can perhaps have more elements than length.
  private final int[] limbs;
  private final int length;

  // Maximum number of significant limbs
  private static final int N_LIMBS = 8;
  // Number of limbs allocated, one more for carries
  private static final int N_ALLOC = 9;

  // --- Getters for testing ---
  int length() {
    return length;
  }

  int[] limbs() {
    return limbs;
  }

  // --- Preallocating small integers 0..nSmallInts ---
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

  // --- Constructors ---

  UInt256(final int[] limbs, final int length) {
    this.limbs = limbs;
    this.length = length;
    // Unchecked length: assumes length is properly set.
  }

  UInt256(final int[] limbs) {
    int i = Math.min(limbs.length - 1, N_ALLOC - 1);
    while ((i >= 0) && (limbs[i] == 0)) i--;
    this.limbs = limbs;
    this.length = Math.min(i + 1, N_LIMBS);
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
    // int[] limbs = new int[len];
    int[] limbs = new int[N_ALLOC];

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

  // ---- conversion ----

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
        return (limbs[0] & 0xFFFFFFFFL);
      }
      default -> {
        return (limbs[0] & 0xFFFFFFFFL) | ((limbs[1] & 0xFFFFFFFFL) << 32);
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
    for (int i = 0; i < length; i++) {
      int offset = 28 - 4 * i;
      int v = limbs[i];
      out[offset] = (byte) (v >>> 24);
      out[offset + 1] = (byte) (v >>> 16);
      out[offset + 2] = (byte) (v >>> 8);
      out[offset + 3] = (byte) v;
    }
    return out;
  }

  // ---- comparison ----

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

  private int[] shiftLeftWithCarry(final int shift) {
    int limbShift = shift / 32;
    int bitShift = shift % 32;
    if (bitShift == 0) {
      return Arrays.copyOfRange(limbs, limbShift, length + limbShift + 1);
    }

    int[] res = new int[length + limbShift + 1];
    int j = limbShift;
    int carry = 0;
    for (int i = 0; i < length; ++i, ++j) {
      res[j] = (limbs[i] << bitShift) | carry;
      carry = limbs[i] >>> (32 - bitShift);
    }
    res[j] = carry; // last carry
    return res;
  }

  /**
   * Shifts value to the left.
   *
   * @param shift number of bits to shift. If negative, shift right instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftLeft(final int shift) {
    if (shift < 0) return shiftRight(-shift);
    if (shift == 0 || isZero()) return this;
    if (shift >= length * 32) return ZERO;
    return new UInt256(shiftLeftWithCarry(shift));
  }

  /**
   * Shifts value to the right.
   *
   * @param shift number of bits to shift. If negative, shift left instead.
   * @return Shifted UInt256 value.
   */
  public UInt256 shiftRight(final int shift) {
    if (shift < 0) return shiftLeft(-shift);
    if (shift == 0 || isZero()) return this;
    if (shift >= length * 32) return ZERO;

    int limbShift = shift / 32;
    int bitShift = shift % 32;
    if (bitShift == 0) {
      return new UInt256(Arrays.copyOfRange(limbs, limbShift, length), length - limbShift);
    }

    int[] res = new int[this.limbs.length - limbShift];
    int j = this.length - 1 - limbShift; // res index
    int carry = 0;
    for (int i = this.length - 1; j >= 0; i--, j--) {
      int r = (limbs[i] >>> bitShift) | carry;
      res[j] = r;
      carry = limbs[i] << (32 - bitShift);
    }
    return new UInt256(res);
  }

  /**
   * Reduce modulo divisor.
   *
   * @param divisor The modulus of the reduction
   * @return The remainder modulo {@code divisor}.
   */
  public UInt256 mod(final UInt256 divisor) {
    if (divisor.isZero()) return ZERO;
    int cmp = compare(this, divisor);
    if (cmp < 0) return this;
    if (cmp == 0) return ZERO;

    int n = divisor.length;
    int m = this.length - n;
    if (n == 1) {
      long d = divisor.limbs[0] & 0xFFFFFFFFL;
      long rem = 0;
      // Process from most significant limb downwards
      for (int i = length - 1; i >= 0; i--) {
        long cur = (rem << 32) | (limbs[i] & 0xFFFFFFFFL);
        rem = Long.remainderUnsigned(cur, d);
      }
      return new UInt256(new int[] {(int) rem});
    }

    // --- Shortcut: divisor fits in 64 bits (2 limbs) ---
    // if (n == 2) {
    //   long d = ((long) divisor.limbs[1] << 32) | (divisor.limbs[0] & 0xFFFFFFFFL);
    //  long rem = 0;
    // Process from most significant limb downwards
    // for (int i = length - 1; i >= 0; i--) {
    //    long cur = (rem << 32) | (limbs[i] & 0xFFFFFFFFL);
    //    rem = Long.remainderUnsigned(cur, d);
    //  }
    //  int lo = (int) rem;
    //  int hi = (int) (rem >>> 32);
    //  return new UInt256(new int[] {lo, hi});
    // }

    // --- Knuth Division ---

    // Normalize
    // Makes 2 copies of limbs: optim ?
    int shift = Integer.numberOfLeadingZeros(divisor.limbs[n - 1]);
    int[] vLimbs = divisor.shiftLeftWithCarry(shift);
    int[] uLimbs = this.shiftLeftWithCarry(shift);

    // Main division loop
    long vn1 = vLimbs[n - 1] & 0xFFFFFFFFL;
    long vn2 = vLimbs[n - 2] & 0xFFFFFFFFL;
    for (int j = m; j >= 0; j--) {
      long ujn = (uLimbs[j + n] & 0xFFFFFFFFL);
      long ujn1 = (uLimbs[j + n - 1] & 0xFFFFFFFFL);
      long ujn2 = (uLimbs[j + n - 2] & 0xFFFFFFFFL);

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
        long prod = (vLimbs[i] & 0xFFFFFFFFL) * qhat;
        long sub = (uLimbs[i + j] & 0xFFFFFFFFL) - (prod & 0xFFFFFFFFL) - borrow;
        uLimbs[i + j] = (int) sub;
        borrow = (prod >>> 32) - (sub >> 32);
      }
      long sub = (uLimbs[j + n] & 0xFFFFFFFFL) - borrow;
      uLimbs[j + n] = (int) sub;

      if (sub < 0) {
        // Add back
        long carry = 0;
        for (int i = 0; i < n; i++) {
          long sum = (uLimbs[i + j] & 0xFFFFFFFFL) + (vLimbs[i] & 0xFFFFFFFFL) + carry;
          uLimbs[i + j] = (int) sum;
          carry = sum >>> 32;
        }
        uLimbs[j + n] = (int) (uLimbs[j + n] + carry);
      }
    }

    // Unnormalize remainder
    return (new UInt256(uLimbs, n)).shiftRight(shift);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("0x");
    for (byte b : toBytesBE()) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
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
}
