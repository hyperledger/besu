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
package org.hyperledger.besu.evm.operation;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;

/**
 * Utility class for shared constants and helpers used by optimized 256-bit shift operations (SHL,
 * SHR, SAR).
 */
public final class Shift256Operations {

  /** An array of 31 0 bytes */
  private static final byte[] ZERO_31 = new byte[31];

  /** All ones (0xFF repeated 32 times). */
  public static final Bytes ALL_ONES = Bytes.repeat((byte) 0xFF, 32);

  /** Raw byte array of ALL_ONES for use with {@code Arrays.equals} (JVM intrinsic). */
  static final byte[] ALL_ONES_BYTES = ALL_ONES.toArrayUnsafe();

  private Shift256Operations() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks whether the EVM shift amount overflows (shift >= 256).
   *
   * <p>If any byte except the last byte is non-zero, the value is >= 256.
   *
   * @param shiftBytes the raw byte array of the shift amount
   * @return {@code true} if the shift amount is >= 256, {@code false} otherwise
   */
  public static boolean isShiftOverflow(final byte[] shiftBytes) {
    final int len = shiftBytes.length - 1;
    if (len <= 0) return false;
    return !Arrays.equals(shiftBytes, 0, len, ZERO_31, 0, len);
  }
}
