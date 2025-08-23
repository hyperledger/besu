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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;

import java.util.Locale;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/** Small, static helpers used by CallTracerResultConverter. */
final class CallTracerHelper {
  private CallTracerHelper() {}

  // ---- Hex/log helpers ------------------------------------------------------

  static String shortHex(final Bytes b, final int maxBytes) {
    if (b == null) return "null";
    final int n = Math.min(b.size(), Math.max(0, maxBytes));
    final String hex = b.slice(0, n).toHexString();
    return b.size() > n ? hex + "...(" + b.size() + "B)" : hex;
  }

  static String hexN(final long v) {
    return "0x" + Long.toHexString(v);
  }

  /** Converts Bytes to unsigned int (clamped to Integer.MAX_VALUE). */
  static int bytesToInt(final Bytes bytes) {
    try {
      final java.math.BigInteger bi = bytes.toUnsignedBigInteger();
      final java.math.BigInteger max = java.math.BigInteger.valueOf(Integer.MAX_VALUE);
      return bi.compareTo(max) > 0 ? Integer.MAX_VALUE : bi.intValue();
    } catch (final Exception e) {
      return 0;
    }
  }

  // ---- Memory helpers -------------------------------------------------------

  /**
   * Slice caller memory at [offset, offset+length) with word-windowing and zero padding. Memory is
   * an array of 32-byte words (Bytes32) as exposed by the tracer.
   */
  static Bytes extractCallDataFromMemory(final Bytes[] memory, final int offset, final int length) {
    if (offset < 0 || length <= 0) return Bytes.EMPTY;

    // Check for unreasonably large values (common in failed calls)
    if (length > 1_000_000) {
      // This often happens with failed calls - return empty
      return Bytes.EMPTY;
    }

    final int startWord = offset >>> 5; // /32
    final int endWord = (offset + length + 31) >>> 5; // ceil((off+len)/32)
    final int wordCount = Math.max(0, endWord - startWord);
    if (wordCount == 0) return Bytes.EMPTY;

    final MutableBytes acc = MutableBytes.create(wordCount * 32);
    for (int w = 0; w < wordCount; w++) {
      final int i = startWord + w;
      final Bytes word = (memory != null && i >= 0 && i < memory.length) ? memory[i] : Bytes32.ZERO;
      word.copyTo(acc, w * 32);
    }

    final int startByteInWord = offset & 31; // %32
    if (startByteInWord + length <= acc.size()) {
      return acc.slice(startByteInWord, length);
    }

    final Bytes slice =
        acc.slice(startByteInWord, Math.max(0, Math.min(length, acc.size() - startByteInWord)));
    final int missing = length - slice.size();
    return (missing <= 0) ? slice : Bytes.concatenate(slice, MutableBytes.create(missing));
  }

  static boolean shouldShowFailedCall(final ExceptionalHaltReason reason) {
    if (reason == null) return false;

    String reasonName = reason.name();

    // Based on Geth behavior and Besu's actual halt reasons
    return "INSUFFICIENT_GAS".equals(reasonName)
        || // Out of gas
        "INSUFFICIENT_STACK_ITEMS".equals(reasonName)
        || // Stack underflow
        "TOO_MANY_STACK_ITEMS".equals(reasonName)
        || // Stack overflow
        "INVALID_OPERATION".equals(reasonName)
        || // Bad instruction
        "ILLEGAL_STATE_CHANGE".equals(reasonName)
        || // Write protection
        "INVALID_JUMP_DESTINATION".equals(reasonName)
        || // Bad jump
        "INVALID_CODE".equals(reasonName)
        || // Invalid code
        "CODE_TOO_LARGE".equals(reasonName)
        || // Code too large
        "PRECOMPILE_ERROR".equals(reasonName); // Precompile failures
  }

  static String mapExceptionalHaltToError(final ExceptionalHaltReason reason) {
    if (reason == null) return "execution reverted";

    // Map Besu's descriptions to Geth-style error messages
    switch (reason.name()) {
      case "INSUFFICIENT_GAS":
        return "out of gas";
      case "INSUFFICIENT_STACK_ITEMS":
        return "stack underflow";
      case "TOO_MANY_STACK_ITEMS":
        return "stack limit reached";
      case "INVALID_OPERATION":
        // Check if it's a specific invalid opcode
        String desc = reason.getDescription();
        if (desc.startsWith("Invalid opcode:")) {
          return desc.toLowerCase(Locale.ROOT); // Use the specific opcode message
        }
        return "invalid opcode";
      case "ILLEGAL_STATE_CHANGE":
        return "write protection";
      case "INVALID_JUMP_DESTINATION":
        return "invalid jump destination";
      case "OUT_OF_BOUNDS":
      case "INVALID_RETURN_DATA_BUFFER_ACCESS":
        return "out of bounds";
      case "CODE_TOO_LARGE":
        return "contract code size exceeds limit";
      case "INVALID_CODE":
        return "invalid code";
      case "PRECOMPILE_ERROR":
        return "precompile failed";
      default:
        // Use Besu's description as fallback
        return reason.getDescription().toLowerCase(Locale.ROOT);
    }
  }
}
