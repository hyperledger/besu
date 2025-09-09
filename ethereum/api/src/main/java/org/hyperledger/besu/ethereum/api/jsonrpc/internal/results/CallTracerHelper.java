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

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/** Small, static helpers used by CallTracerResultConverter. */
final class CallTracerHelper {
  // Memory and stack constants
  private static final int BYTES_PER_WORD = 32;
  private static final int WORD_SIZE_SHIFT = 5; // For division by 32 (2^5)
  private static final int WORD_MASK = 31; // For modulo 32 (2^5 - 1)

  private CallTracerHelper() {}

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

    final int startWord = offset >>> WORD_SIZE_SHIFT; // /32
    final int endWord = (offset + length + WORD_MASK) >>> WORD_SIZE_SHIFT; // ceil((off+len)/32)
    final int wordCount = Math.max(0, endWord - startWord);
    if (wordCount == 0) return Bytes.EMPTY;

    final MutableBytes acc = MutableBytes.create(wordCount * BYTES_PER_WORD);
    for (int w = 0; w < wordCount; w++) {
      final int i = startWord + w;
      final Bytes word = (memory != null && i >= 0 && i < memory.length) ? memory[i] : Bytes32.ZERO;
      word.copyTo(acc, w * BYTES_PER_WORD);
    }

    final int startByteInWord = offset & WORD_MASK; // %32
    if (startByteInWord + length <= acc.size()) {
      return acc.slice(startByteInWord, length);
    }

    final Bytes slice =
        acc.slice(startByteInWord, Math.max(0, Math.min(length, acc.size() - startByteInWord)));
    final int missing = length - slice.size();
    return (missing <= 0) ? slice : Bytes.concatenate(slice, MutableBytes.create(missing));
  }
}
