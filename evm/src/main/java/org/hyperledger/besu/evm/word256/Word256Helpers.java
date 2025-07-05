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
