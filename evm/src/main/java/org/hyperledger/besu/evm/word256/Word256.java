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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

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

    // skip padding
    if (bytes.length == 32) {
      return fromBytesUnsafe(bytes);
    }

    final byte[] padded = new byte[32];
    System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length);
    return setFromByteArray(padded);
  }

  /**
   * Creates a Word256 from a Bytes object.
   *
   * <p>This method is optimized to reuse the backing array if it is exactly 32 bytes long. If the
   * Bytes object is shorter, it will be zero-padded to fit the 256-bit representation.
   *
   * @param bytes the Bytes object to convert
   * @return a new Word256 instance representing the Bytes object
   */
  public static Word256 fromBytes(final Bytes bytes) {
    if (bytes.size() == 32) {
      // Reuse the backing array if we can
      return fromBytesUnsafe(bytes.toArrayUnsafe());
    }

    // Fallback: zero-pad if needed
    return fromBytes(bytes.toArrayUnsafe());
  }

  /**
   * Creates a Word256 from a byte array without checking its length.
   *
   * <p>This method is intended for internal use where the caller guarantees that the byte array is
   * exactly 32 bytes long. It does not perform any padding or validation.
   *
   * @param bytes the byte array to convert
   * @return a new Word256 instance representing the byte array
   */
  private static Word256 fromBytesUnsafe(final byte[] bytes) {
    return setFromByteArray(bytes);
  }

  /**
   * Creates a Word256 from a byte array, padding it to 32 bytes if necessary.
   *
   * <p>This method is used internally to create a Word256 with a cached byte array representation.
   * It ensures that the byte array is always 32 bytes long, padding with leading zeros if needed.
   *
   * @param bytes the byte array to convert
   * @return a new Word256 instance representing the byte array
   */
  private static Word256 setFromByteArray(final byte[] bytes) {
    final long l3 = Word256Helpers.bytesToLong(bytes, 0);
    final long l2 = Word256Helpers.bytesToLong(bytes, 8);
    final long l1 = Word256Helpers.bytesToLong(bytes, 16);
    final long l0 = Word256Helpers.bytesToLong(bytes, 24);

    return new Word256(l0, l1, l2, l3, bytes);
  }

  /**
   * Converts this Word256 to a byte array.
   *
   * <p>The byte array will always be 32 bytes long, padded with leading zeros if necessary.
   *
   * @return a byte array representation of this Word256
   */
  public byte[] toBytesArray() {
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
   * Converts this Word256 to a Bytes32 object.
   *
   * <p>This method is optimized to reuse the cached byte array if available. If not, it creates a
   * new byte array representation of the Word256.
   *
   * @return a Bytes32 object representing this Word256
   */
  public Bytes32 toBytes32() {
    if (bytesCache != null) {
      return Bytes32.wrap(bytesCache);
    }

    // Create a new byte array and write the long values into it
    final byte[] tmp = new byte[32];
    Word256Helpers.writeToByteArray(tmp, 0, l3);
    Word256Helpers.writeToByteArray(tmp, 8, l2);
    Word256Helpers.writeToByteArray(tmp, 16, l1);
    Word256Helpers.writeToByteArray(tmp, 24, l0);

    return Bytes32.wrap(tmp);
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
   * Multiplies this Word256 by another Word256.
   *
   * @param other the Word256 to multiply with
   * @return a new Word256 representing the product of this and other
   */
  public Word256 mul(final Word256 other) {
    return Word256Arithmetic.mul(this, other);
  }

  /**
   * Raises this Word256 to the power of another Word256.
   *
   * @param exponent the Word256 exponent
   * @return a new Word256 representing this raised to the power of exponent
   */
  public Word256 exp(final Word256 exponent) {
    return Word256Arithmetic.exp(this, exponent);
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

  /**
   * Checks if this Word256 is zero.
   *
   * @return true if this Word256 is zero, false otherwise
   */
  public boolean isZero() {
    return Word256Comparison.isZero(this);
  }

  /**
   * Return the bit at the specified index in this Word256.
   *
   * <p>The index must be in the range [0, 255]. The least significant bit is at index 0, and the
   * most significant bit is at index 255.
   *
   * @param index the bit index (0-255)
   * @return the bit
   */
  public int getBit(final int index) {
    return Word256Bitwise.getBit(this, index);
  }

  /**
   * Returns the number of bits that are non-zero in this Word256.
   *
   * <p>This is the total number of bits from the most significant bit down to the least significant
   * bit that are non-zero. For example, if the most significant bit is zero but the next one is
   * not, this will return 192, indicating that the Word256 has 192 significant bits.
   *
   * @return the number of significant bits (0-256)
   */
  public int bitLength() {
    if (l3 != 0) {
      return 256 - Long.numberOfLeadingZeros(l3);
    }

    if (l2 != 0) {
      return 192 - Long.numberOfLeadingZeros(l2);
    }

    if (l1 != 0) {
      return 128 - Long.numberOfLeadingZeros(l1);
    }

    if (l0 != 0) {
      return 64 - Long.numberOfLeadingZeros(l0);
    }

    return 0;
  }
}
