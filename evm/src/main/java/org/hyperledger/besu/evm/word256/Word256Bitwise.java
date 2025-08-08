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
 * Utility class for performing bitwise operations on 256-bit words represented by the {@link
 * Word256} class.
 *
 * <p>This class provides methods for shifting, getting, setting bits, and performing bitwise
 * operations such as AND, OR, XOR, and NOT.
 */
final class Word256Bitwise {

  private Word256Bitwise() {}

  /**
   * Performs a bitwise AND operation on two Word256 values.
   *
   * @param a the first Word256 value
   * @param b the second Word256 value
   * @return a new Word256 value representing the bitwise AND of a and b
   */
  static Word256 and(final Word256 a, final Word256 b) {
    return new Word256(a.l0 & b.l0, a.l1 & b.l1, a.l2 & b.l2, a.l3 & b.l3);
  }

  /**
   * Gets the bit at the specified index from the given Word256 value.
   *
   * @param a the Word256 value
   * @param index the bit index (0-255)
   * @return 1 if the bit is set, 0 if it is not
   * @throws IllegalArgumentException if the index is out of range
   */
  static int getBit(final Word256 a, final int index) {
    if (index < 0 || index >= 256) {
      throw new IllegalArgumentException("bit index out of range: " + index);
    }
    final int word = index / 64;
    final int bit = index % 64;
    final long mask = 1L << bit;

    return switch (word) {
      case 0 -> (a.l0 & mask) != 0 ? 1 : 0;
      case 1 -> (a.l1 & mask) != 0 ? 1 : 0;
      case 2 -> (a.l2 & mask) != 0 ? 1 : 0;
      case 3 -> (a.l3 & mask) != 0 ? 1 : 0;
      default -> throw new AssertionError();
    };
  }
}
