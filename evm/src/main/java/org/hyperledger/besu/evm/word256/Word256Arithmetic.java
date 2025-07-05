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
 * * Utility class for performing arithmetic operations on {@link Word256} values.
 *
 * <p>This class provides methods for addition, subtraction, multiplication, division, and modular
 * arithmetic on 256-bit words represented by four 64-bit long values.
 */
final class Word256Arithmetic {

  private Word256Arithmetic() {}

  /**
   * Adds two Word256 values.
   *
   * @param a the first Word256 value
   * @param b the second Word256 value
   * @return the sum of a and b as a new Word256
   */
  static Word256 add(final Word256 a, final Word256 b) {
    long r0 = a.l0 + b.l0;
    long carry = Long.compareUnsigned(r0, a.l0) < 0 ? 1 : 0;

    long r1 = a.l1 + b.l1 + carry;
    carry = Long.compareUnsigned(r1, a.l1) < 0 || (carry == 1 && r1 == a.l1) ? 1 : 0;

    long r2 = a.l2 + b.l2 + carry;
    carry = Long.compareUnsigned(r2, a.l2) < 0 || (carry == 1 && r2 == a.l2) ? 1 : 0;

    long r3 = a.l3 + b.l3 + carry;

    return new Word256(r0, r1, r2, r3);
  }

  /**
   * Subtracts one Word256 value from another.
   *
   * @param a the Word256 value to subtract from
   * @param b the Word256 value to subtract
   * @return the result of a - b as a new Word256
   */
  static Word256 sub(final Word256 a, final Word256 b) {
    long[] r;
    long r0, r1, r2, r3;
    long borrow;

    r = Word256Helpers.subtract64(a.l0, b.l0, 0);
    r0 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l1, b.l1, borrow);
    r1 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l2, b.l2, borrow);
    r2 = r[0];
    borrow = r[1];

    r = Word256Helpers.subtract64(a.l3, b.l3, borrow);
    r3 = r[0];

    return new Word256(r0, r1, r2, r3);
  }
}
