/*
 * Copyright contributors to Hyperledger Besu.
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

/**
 * Shared decoder tables for EIP-8024 operations (DUPN, SWAPN, EXCHANGE).
 *
 * <p>EIP-8024 uses special immediate encoding to avoid bytes that could be confused with JUMPDEST
 * (0x5b) or PUSH opcodes (0x60-0x7f), ensuring backward compatibility with existing deployed
 * contracts.
 */
// Thread-safety note: All static arrays in this class are effectively immutable.
// They are populated once during class initialization and never modified thereafter,
// making them safe for concurrent read access in a multi-threaded environment.
@SuppressWarnings("MutablePublicArray") // Performance: hot-path direct array access is intentional
public final class Eip8024Decoder {

  private Eip8024Decoder() {
    // Utility class
  }

  // ==================== Single Operand Decoding (DUPN, SWAPN) ====================

  /**
   * Decode table for single operand instructions (DUPN, SWAPN). Maps immediate byte value to
   * decoded n value. Valid range: 0-90 maps to 17-107, 128-255 maps to 108-235. Invalid range:
   * 91-127.
   */
  public static final int[] DECODE_SINGLE = new int[256];

  /**
   * Validity table for single operand immediates. True for valid immediates (0-90, 128-255), false
   * for invalid (91-127).
   */
  public static final boolean[] VALID_SINGLE = new boolean[256];

  // ==================== Pair Operand Decoding (EXCHANGE) ====================

  /**
   * Decode table for first index (n) in pair operand instructions (EXCHANGE). Valid range: 0-79 and
   * 128-255. Invalid range: 80-127.
   */
  public static final int[] DECODE_PAIR_N = new int[256];

  /**
   * Decode table for second index (m) in pair operand instructions (EXCHANGE). Valid range: 0-79
   * and 128-255. Invalid range: 80-127.
   */
  public static final int[] DECODE_PAIR_M = new int[256];

  /**
   * Validity table for pair operand immediates. True for valid immediates (0-79, 128-255), false
   * for invalid (80-127).
   */
  public static final boolean[] VALID_PAIR = new boolean[256];

  static {
    initializeSingleDecodeTables();
    initializePairDecodeTables();
  }

  /**
   * Initialize decode tables for single operand per EIP-8024 decode_single specification:
   *
   * <pre>
   * if x <= 90: n = x + 17 (range 17 to 107)
   * if x >= 128: n = x - 20 (range 108 to 235)
   * if 91 <= x <= 127: invalid (preserves JUMPDEST 0x5b and PUSH 0x60-0x7f)
   * </pre>
   */
  private static void initializeSingleDecodeTables() {
    for (int x = 0; x <= 90; x++) {
      DECODE_SINGLE[x] = x + 17;
      VALID_SINGLE[x] = true;
    }
    for (int x = 128; x <= 255; x++) {
      DECODE_SINGLE[x] = x - 20;
      VALID_SINGLE[x] = true;
    }
    // 91-127 remain at default values (0 and false)
  }

  /**
   * Initialize decode tables for pair operand per EIP-8024 decode_pair specification:
   *
   * <pre>
   * k = x if x <= 79 else x - 48
   * q, r = divmod(k, 16)
   * if q < r: return (q + 1, r + 1)
   * else: return (r + 1, 29 - q)
   * </pre>
   *
   * <p>Valid range: 0-79 and 128-255. Invalid range: 80-127.
   */
  private static void initializePairDecodeTables() {
    for (int x = 0; x <= 79; x++) {
      decodePairIntoTables(x, x);
      VALID_PAIR[x] = true;
    }
    for (int x = 128; x <= 255; x++) {
      decodePairIntoTables(x, x - 48);
      VALID_PAIR[x] = true;
    }
    // 80-127 remain at default values (0, 0, false)
  }

  private static void decodePairIntoTables(final int x, final int k) {
    final int q = k / 16;
    final int r = k % 16;
    if (q < r) {
      DECODE_PAIR_N[x] = q + 1;
      DECODE_PAIR_M[x] = r + 1;
    } else {
      DECODE_PAIR_N[x] = r + 1;
      DECODE_PAIR_M[x] = 29 - q;
    }
  }

  /**
   * Decodes a single immediate byte to the stack index n.
   *
   * @param imm the immediate byte value (0-255)
   * @return the decoded n value, or -1 if the immediate is invalid
   */
  public static int decodeSingle(final int imm) {
    if (imm < 0 || imm > 255 || !VALID_SINGLE[imm]) {
      return -1;
    }
    return DECODE_SINGLE[imm];
  }

  /**
   * Decodes a pair immediate byte to the stack indices n and m.
   *
   * @param imm the immediate byte value (0-255)
   * @return an array of [n, m], or null if the immediate is invalid
   */
  public static int[] decodePair(final int imm) {
    if (imm < 0 || imm > 255 || !VALID_PAIR[imm]) {
      return null;
    }
    return new int[] {DECODE_PAIR_N[imm], DECODE_PAIR_M[imm]};
  }
}
