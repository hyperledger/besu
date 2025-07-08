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
package org.hyperledger.besu.evm.word256;

/**
 * Internal helpers for Word256 that support full-width multiplication and 512-bit modular
 * reduction.
 */
final class Word256Helpers {

  private Word256Helpers() {
    // Prevent instantiation
  }

  /** Multiplies two 64-bit integers and returns the result as a long array of two elements. */
  static long[] subtract64(final long x, final long y, final long borrowIn) {
    final long diff = x - y - borrowIn;

    // Bitwise computation of borrowOut: ((~x & y) | (~(x ^ y) & diff)) >>> 63
    final long t1 = ~x & y;
    final long t2 = ~(x ^ y) & diff;
    final long borrowOut = (t1 | t2) >>> 63;

    return new long[] {diff, borrowOut};
  }

  /**
   * Multiplies two unsigned 64-bit integers and returns the result as a long array of two elements:
   * the high part and the low part of the product.
   *
   * @param x the first unsigned 64-bit integer
   * @param y the second unsigned 64-bit integer
   * @return an array containing the high and low parts of the product
   */
  static long[] multiplyHighLowUnsigned(final long x, final long y) {
    final long x0 = x & 0xFFFFFFFFL;
    final long x1 = x >>> 32;
    final long y0 = y & 0xFFFFFFFFL;
    final long y1 = y >>> 32;

    final long w0 = x0 * y0;
    final long t = x1 * y0 + (w0 >>> 32);
    final long w1 = t & 0xFFFFFFFFL;
    final long w2 = t >>> 32;

    final long lo = x * y;
    final long hi = x1 * y1 + w2 + ((x0 * y1 + w1) >>> 32);

    return new long[] {hi, lo};
  }

  /**
   * Multiplies two unsigned 64-bit integers and adds a third unsigned 64-bit integer to the low
   * part of the product.
   *
   * @param z the unsigned 64-bit integer to add to the low part of the product
   * @param x the first unsigned 64-bit integer
   * @param y the second unsigned 64-bit integer
   * @return an array containing the high part and the low part of the result
   */
  static long[] unsignedMultiplyAdd(final long z, final long x, final long y) {
    final long[] m = multiplyHighLowUnsigned(x, y);
    final long loSum = m[1] + z;
    final boolean carry = Long.compareUnsigned(loSum, m[1]) < 0;
    final long hi = m[0] + (carry ? 1 : 0);
    return new long[] {hi, loSum};
  }

  /**
   * Multiplies two unsigned 64-bit integers, adds a third unsigned 64-bit integer to the low part
   * of the product, and includes a carry from a previous operation.
   *
   * @param z the unsigned 64-bit integer to add to the low part of the product
   * @param x the first unsigned 64-bit integer
   * @param y the second unsigned 64-bit integer
   * @param carry the carry from a previous operation
   * @return an array containing the high part and the low part of the result
   */
  static long[] unsignedMultiplyAddWithCarry(
      final long z, final long x, final long y, final long carry) {
    final long[] m = multiplyHighLowUnsigned(x, y);
    final long loMul = m[1];
    final long hiMul = m[0];

    // lo1 = loMul + carry
    final long lo1 = loMul + carry;
    final boolean carry1 = Long.compareUnsigned(lo1, loMul) < 0;

    // lo2 = lo1 + z
    final long lo2 = lo1 + z;
    final boolean carry2 = Long.compareUnsigned(lo2, lo1) < 0;

    // hi = hiMul + carry1 + carry2
    final long hi1 = hiMul + (carry1 ? 1 : 0);
    final long hi2 = hi1 + (carry2 ? 1 : 0);

    return new long[] {hi2, lo2};
  }

  /**
   * Multiplies three pairs of unsigned 64-bit integers and adds their products.
   *
   * @param x1 the first unsigned 64-bit integer of the first pair
   * @param y1 the second unsigned 64-bit integer of the first pair
   * @param x2 the first unsigned 64-bit integer of the second pair
   * @param y2 the second unsigned 64-bit integer of the second pair
   * @param x3 the first unsigned 64-bit integer of the third pair
   * @param y3 the second unsigned 64-bit integer of the third pair
   * @return the sum of the products as a long
   */
  static long unsignedMulAdd3(
      final long x1, final long y1, final long x2, final long y2, final long x3, final long y3) {
    return x1 * y1 + x2 * y2 + x3 * y3;
  }

  /**
   * Multiplies two unsigned 64-bit integers and adds a variable number of additional unsigned
   * integers to the result.
   *
   * @param x the first unsigned 64-bit integer
   * @param y the second unsigned 64-bit integer
   * @param extras additional unsigned integers to add to the product
   * @return the result of the multiplication and addition as a long
   */
  static long unsignedMultiplyAndAdd(final long x, final long y, final long... extras) {
    long result = x * y;
    for (final long e : extras) {
      result = result + e;
    }
    return result;
  }

  /**
   * Converts a byte array to a long value, interpreting the bytes as a big-endian 64-bit integer.
   *
   * @param bytes the byte array
   * @param offset the offset in the byte array
   * @return the long value represented by the bytes
   */
  static long bytesToLong(final byte[] bytes, final int offset) {
    return ((bytes[offset] & 0xFFL) << 56)
        | ((bytes[offset + 1] & 0xFFL) << 48)
        | ((bytes[offset + 2] & 0xFFL) << 40)
        | ((bytes[offset + 3] & 0xFFL) << 32)
        | ((bytes[offset + 4] & 0xFFL) << 24)
        | ((bytes[offset + 5] & 0xFFL) << 16)
        | ((bytes[offset + 6] & 0xFFL) << 8)
        | ((bytes[offset + 7] & 0xFFL));
  }

  /** Writes a long value as 8 bytes big-endian into the target at the specified offset. */
  static void writeToByteArray(final byte[] dest, final int offset, final long value) {
    for (int i = 0; i < 8; i++) {
      dest[offset + i] = (byte) (value >>> (56 - (i * 8)));
    }
  }
}
