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
 * Utility class for defining constants for Word256 values.
 *
 * <p>This class provides commonly used Word256 constants such as ZERO, ONE, MINUS_ONE, and MAX.
 */
final class Word256Constants {

  private Word256Constants() {
    // Prevent instantiation
  }

  static final Word256 ZERO = new Word256(0L, 0L, 0L, 0L);

  static final Word256 ONE = new Word256(1L, 0L, 0L, 0L);

  static final Word256 MINUS_ONE = new Word256(-1L, -1L, -1L, -1L);

  static final Word256 MAX = new Word256(~0L, ~0L, ~0L, ~0L);

  static final Word256 FULL_MASK =
      new Word256(
          0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);
}
