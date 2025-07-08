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

/** Utility class for performing comparisons on {@link Word256} values. */
final class Word256Comparison {

  private Word256Comparison() {
    // Prevent instantiation
  }

  /**
   * Checks if a Word256 value is zero.
   *
   * @param w the Word256 value to check
   * @return true if w is zero, false otherwise
   */
  static boolean isZero(final Word256 w) {
    return w.l0 == 0 && w.l1 == 0 && w.l2 == 0 && w.l3 == 0;
  }
}
