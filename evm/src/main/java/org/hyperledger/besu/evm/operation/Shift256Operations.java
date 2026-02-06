/*
 * Copyright ConsenSys AG.
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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Utility class for shared constants and helpers used by optimized 256-bit shift operations (SHL,
 * SHR, SAR).
 */
public final class Shift256Operations {

  /** Zero value (32 zero bytes). */
  public static final Bytes ZERO_32 = Bytes32.ZERO;

  /** All ones (0xFF repeated 32 times). */
  public static final Bytes ALL_ONES = Bytes.repeat((byte) 0xFF, 32);

  private Shift256Operations() {
    // Utility class - prevent instantiation
  }

  /**
   * Checks whether the EVM shift amount overflows (shift >= 256).
   *
   * <p>If any byte except the last byte is non-zero, the value is >= 256.
   *
   * @param shiftAmount the shift amount as a stack value
   * @return {@code true} if the shift amount is >= 256, {@code false} otherwise
   */
  public static boolean isShiftOverflow(final Bytes shiftAmount) {
    final byte[] a = shiftAmount.toArrayUnsafe();
    final int n = a.length;
    for (int i = 0; i < n - 1; i++) {
      if (a[i] != 0) return true;
    }
    return false;
  }
}
