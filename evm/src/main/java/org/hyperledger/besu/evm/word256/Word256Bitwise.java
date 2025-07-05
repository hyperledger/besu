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
}
