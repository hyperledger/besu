/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.internal;

import org.hyperledger.besu.datatypes.Address;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt256;

/** Static utility methods to work with VM words (that is, {@link Bytes32} values). */
public interface Words {
  /**
   * Creates a new word containing the provided address.
   *
   * @param address The address to convert to a word.
   * @return A VM word containing {@code address} (left-padded as according to the VM specification
   *     (Appendix H. of the Yellow paper)).
   */
  static UInt256 fromAddress(final Address address) {
    return UInt256.fromBytes(Bytes32.leftPad(address));
  }

  /**
   * Extract an address from the provided address.
   *
   * @param bytes The word to extract the address from.
   * @return An address build from the right-most 160-bits of the {@code bytes} (as according to the
   *     VM specification (Appendix H. of the Yellow paper)).
   */
  static Address toAddress(final Bytes bytes) {
    final int size = bytes.size();
    if (size < 20) {
      final MutableBytes result = MutableBytes.create(20);
      bytes.copyTo(result, 20 - size);
      // Addresses get hashed alot in calls, and mutable bytes don't cache the `hashCode`
      // so always return an immutable copy
      return Address.wrap(result.copy());
    } else if (size == 20) {
      return Address.wrap(bytes);
    } else {
      return Address.wrap(bytes.slice(size - Address.SIZE, Address.SIZE));
    }
  }

  /**
   * The number of words corresponding to the provided input.
   *
   * <p>In other words, this computes {@code input.size() / 32} but rounded up.
   *
   * @param input the input to check.
   * @return the number of (32 bytes) words that {@code input} spans.
   */
  static int numWords(final Bytes input) {
    // m/n round up == (m + n - 1)/n: http://www.cs.nott.ac.uk/~psarb2/G51MPC/slides/NumberLogic.pdf
    return (input.size() + Bytes32.SIZE - 1) / Bytes32.SIZE;
  }

  /**
   * The value of the bytes as though it was representing an unsigned integer, however if the value
   * exceeds Long.MAX_VALUE then Long.MAX_VALUE will be returned.
   *
   * @param uint the unsigned integer
   * @return the least of the integer value or Long.MAX_VALUE
   */
  static long clampedToLong(final Bytes uint) {
    if (uint.size() <= 8) {
      final long result = uint.toLong();
      return result < 0 ? Long.MAX_VALUE : result;
    }

    final Bytes trimmed = uint.trimLeadingZeros();
    if (trimmed.size() <= 8) {
      final long result = trimmed.toLong();
      return result < 0 ? Long.MAX_VALUE : result;
    } else {
      // clamp to the largest int.
      return Long.MAX_VALUE;
    }
  }

  /**
   * The value of the long as though it was representing an unsigned integer, however if the value
   * is out of range it will return the number at the end of the range.
   *
   * @param l the signed integer
   * @return The int value, or Integer.MAX_VALUE if too large or Integer.MIN_VALUE if to small.
   */
  static int clampedToInt(final long l) {
    if (l > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    } else if (l < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    } else {
      return (int) l;
    }
  }

  /**
   * Adds a and b, but if an underflow/overflow occurs return the Long max/min value
   *
   * @param a first value
   * @param b second value
   * @return value of a plus b if no over/underflows or Long.MAX_VALUE/Long.MIN_VALUE otherwise
   */
  static long clampedAdd(final long a, final long b) {
    try {
      return Math.addExact(a, b);
    } catch (final ArithmeticException ae) {
      return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
    }
  }

  /**
   * Multiplies a and b, but if an underflow/overflow occurs return the Long max/min value
   *
   * @param a first value
   * @param b second value
   * @return value of a times b if no over/underflows or Long.MAX_VALUE/Long.MIN_VALUE otherwise
   */
  static long clampedMultiply(final long a, final long b) {
    try {
      return Math.multiplyExact(a, b);
    } catch (final ArithmeticException ae) {
      return ((a ^ b) < 0) ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
  }

  /**
   * Multiplies a and b, but if an underflow/overflow occurs return the Integer max/min value
   *
   * @param a first value
   * @param b second value
   * @return value of a times b if no over/underflows or Integer.MAX_VALUE/Integer.MIN_VALUE
   *     otherwise
   */
  static int clampedMultiply(final int a, final int b) {
    try {
      return Math.multiplyExact(a, b);
    } catch (final ArithmeticException ae) {
      return ((a ^ b) < 0) ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    }
  }

  /**
   * Returns the lesser of the two values, when compared as an unsigned value
   *
   * @param a first value
   * @param b second value
   * @return a if, as an unsigned integer, a is less than b; otherwise b.
   */
  static long unsignedMin(final long a, final long b) {
    return Long.compareUnsigned(a, b) < 0 ? a : b;
  }

  static int readBigEndianU16(final int index, final byte[] array) {
    if (index + 1 >= array.length) {
      throw new IndexOutOfBoundsException();
    }
    return ((array[index] & 0xff) << 8) | (array[index + 1] & 0xff);
  }

  static int readBigEndianI16(final int index, final byte[] array) {
    if (index + 1 >= array.length) {
      throw new IndexOutOfBoundsException();
    }
    return (array[index] << 8) | (array[index + 1] & 0xff);
  }
}
