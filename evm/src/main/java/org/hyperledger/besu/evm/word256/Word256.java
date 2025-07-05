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
 * A fixed-size, immutable 256-bit unsigned integer backed by four {@code long} values.
 *
 * <p>This class is designed for high-performance applications such as Ethereum Virtual Machine
 * (EVM) execution, cryptographic arithmetic, and protocol internals. Internally, the word is
 * represented as four 64-bit fields, from least to most significant:
 *
 * <pre>
 *   l0 (bits 63..0), l1 (127..64), l2 (191..128), l3 (255..192)
 * </pre>
 *
 * <p>All arithmetic, logical, and modular operations are implemented using 64-bit math with full
 * carry/borrow handling. No {@link java.math.BigInteger} or heap allocation is used in core paths.
 * Bit-level access and constant-time operations are supported where relevant.
 *
 * <p>To keep the core class compact and JIT-friendly, operations are grouped in package-private
 * helper classes by type (e.g. {@code Word256Arithmetic}, {@code Word256Bitwise}). This design
 * avoids polluting the main API while enabling static dispatch and inlining.
 *
 * <p><strong>Key properties:</strong>
 *
 * <ul>
 *   <li>Immutable, thread-safe, allocation-free once constructed
 *   <li>Semantics match EVM 256-bit word arithmetic exactly
 *   <li>Supports all EVM-relevant operations: {@code ADD}, {@code MUL}, {@code MOD}, etc.
 * </ul>
 */
public final class Word256 {
  /** Zero value for Word256 */
  public static final Word256 ZERO = Word256Constants.ZERO;

  /** One value for Word256 */
  public static final Word256 ONE = Word256Constants.ONE;

  /** Negative one value for Word256 */
  public static final Word256 MINUS_ONE = Word256Constants.MINUS_ONE;

  /** Maximum value for Word256 */
  public static final Word256 MAX = Word256Constants.MAX;

  final long l0, l1, l2, l3;

  private byte[] bytesCache;

  /**
   * Constructs a Word256 from four long values.
   *
   * <p>Each long represents 64 bits of the 256-bit word, with {@code l0} being the least
   * significant and {@code l3} being the most significant.
   *
   * @param l0 the least significant 64 bits
   * @param l1 the next 64 bits
   * @param l2 the next 64 bits
   * @param l3 the most significant 64 bits
   */
  Word256(final long l0, final long l1, final long l2, final long l3) {
    this.l0 = l0;
    this.l1 = l1;
    this.l2 = l2;
    this.l3 = l3;
  }

  /**
   * Constructs a Word256 from four long values and a byte array.
   *
   * <p>This constructor is used internally to create a Word256 with a cached byte array
   * representation. The byte array must be exactly 32 bytes long, representing the 256-bit word.
   *
   * @param l0 the least significant 64 bits
   * @param l1 the next 64 bits
   * @param l2 the next 64 bits
   * @param l3 the most significant 64 bits
   * @param bytes the byte array representation of the Word256
   */
  private Word256(final long l0, final long l1, final long l2, final long l3, final byte[] bytes) {
    this(l0, l1, l2, l3);
    this.bytesCache = bytes;
  }

  /**
   * Creates a Word256 from a byte array.
   *
   * <p>The byte array must be at most 32 bytes long. If it is shorter, it will be padded with
   * leading zeros to fit the 256-bit representation.
   *
   * @param bytes the byte array to convert
   * @return a new Word256 instance representing the byte array
   * @throws IllegalArgumentException if the byte array is longer than 32 bytes
   */
  public static Word256 fromBytes(final byte[] bytes) {
    if (bytes.length > 32) {
      throw new IllegalArgumentException("Word256 input must be at most 32 bytes");
    }
    final byte[] padded = new byte[32];
    System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
    final long l3 = Word256Helpers.bytesToLong(padded, 0);
    final long l2 = Word256Helpers.bytesToLong(padded, 8);
    final long l1 = Word256Helpers.bytesToLong(padded, 16);
    final long l0 = Word256Helpers.bytesToLong(padded, 24);
    return new Word256(l0, l1, l2, l3, padded);
  }

  /**
   * Converts this Word256 to a byte array.
   *
   * <p>The byte array will always be 32 bytes long, padded with leading zeros if necessary.
   *
   * @return a byte array representation of this Word256
   */
  public byte[] toBytes() {
    if (bytesCache != null) {
      return bytesCache;
    }
    // Create a new byte array and write the long values into it
    bytesCache = new byte[32];
    Word256Helpers.writeToByteArray(bytesCache, 0, l3);
    Word256Helpers.writeToByteArray(bytesCache, 8, l2);
    Word256Helpers.writeToByteArray(bytesCache, 16, l1);
    Word256Helpers.writeToByteArray(bytesCache, 24, l0);

    return bytesCache;
  }

  /**
   * Adds this Word256 to another Word256.
   *
   * @param other the other Word256 to add
   * @return a new Word256 representing the sum of this and other
   */
  public Word256 add(final Word256 other) {
    return Word256Arithmetic.add(this, other);
  }

  /**
   * Subtracts another Word256 from this Word256.
   *
   * @param other the Word256 to subtract
   * @return a new Word256 representing the result of this - other
   */
  public Word256 sub(final Word256 other) {
    return Word256Arithmetic.sub(this, other);
  }

  /**
   * Performs a bitwise AND operation with another Word256.
   *
   * @param other the Word256 to AND with
   * @return a new Word256 representing the result of this AND other
   * @throws NullPointerException if other is null
   */
  public Word256 and(final Word256 other) {
    return Word256Bitwise.and(this, other);
  }
}
