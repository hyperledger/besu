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
}
