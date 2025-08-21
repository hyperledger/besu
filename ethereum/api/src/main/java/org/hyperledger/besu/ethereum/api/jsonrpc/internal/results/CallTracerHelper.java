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

  // ---- Precompile helpers ---------------------------------------------------

  /** Detect precompile addresses: 0x...01 through 0x...0a (inclusive). */
  static boolean isPrecompileAddress(final String to) {
    if (to == null) return false;
    final String s = (to.startsWith("0x") || to.startsWith("0X")) ? to.substring(2) : to;
    if (s.length() != 40) return false;

    // First 19 bytes must be zero
    for (int i = 0; i < 38; i++) {
      if (s.charAt(i) != '0') return false;
    }

    // Last byte: 0x01..0x0a (0x0a = KZG point evaluation precompile)
    final int lastByte;
    try {
      lastByte = Integer.parseInt(s.substring(38, 40), 16);
    } catch (NumberFormatException e) {
      return false;
    }
    return lastByte >= 0x01 && lastByte <= 0x0a;
  }

  /** Parse precompile id (last byte) from a hex address; -1 if unknown. */
  static int parsePrecompileIdFromTo(final String to) {
    if (to == null) return -1;
    final String s = (to.startsWith("0x") || to.startsWith("0X")) ? to.substring(2) : to;
    if (s.length() != 40) return -1;
    try {
      return Integer.parseInt(s.substring(38, 40), 16);
    } catch (Exception ignored) {
      return -1;
    }
  }

  // ---- CALL-like IO mapping (robust head/tail) ------------------------------

  private static final int MAX_REASONABLE_SIZE = 1 << 20; // 1 MiB

  record IoCoords(int inOffset, int inSize, int outOffset, int outSize) {}

  private static IoCoords fromTail(final Bytes[] stack) {
    final int n = stack.length;
    // CALL-like tail: [..., gas, to, inOffset, inSize, outOffset, outSize]^TOS
    final int inOffset = bytesToInt(stack[n - 4]);
    final int inSize = bytesToInt(stack[n - 3]);
    final int outOffset = bytesToInt(stack[n - 2]);
    final int outSize = bytesToInt(stack[n - 1]);
    return new IoCoords(inOffset, inSize, outOffset, outSize);
  }

  private static IoCoords fromHead(final Bytes[] stack) {
    // Some frames expose TOS at index 0: [outSize, outOffset, inSize, inOffset, to, gas, ...]
    final int inOffset = bytesToInt(stack[3]);
    final int inSize = bytesToInt(stack[2]);
    final int outOffset = bytesToInt(stack[1]);
    final int outSize = bytesToInt(stack[0]);
    return new IoCoords(inOffset, inSize, outOffset, outSize);
  }

  private static int memBytes(final Bytes[] mem) {
    return (mem == null) ? Integer.MAX_VALUE : (mem.length * 32);
  }

  private static int scoreCoords(final IoCoords c, final Bytes[] preMem) {
    final int m = memBytes(preMem);
    int s = 0;
    if (c.inOffset >= 0 && c.inSize >= 0) s += 2;
    if (c.outOffset >= 0 && c.outSize >= 0) s += 1;
    if (m != Integer.MAX_VALUE) {
      if (c.inOffset + c.inSize <= m) s += 3;
      if (c.outOffset <= m) s += 1;
    }
    if ((c.inOffset & 31) == 0) s += 2;
    if ((c.outOffset & 31) == 0) s += 1;
    if (c.inSize <= MAX_REASONABLE_SIZE) s += 1;
    if (c.outSize <= MAX_REASONABLE_SIZE) s += 1;
    if (c.inSize <= 4096) s += 1;
    return s;
  }

  /** Choose sensible (inOffset,inSize,outOffset,outSize) for CALL-like ops. */
  static IoCoords chooseCallLikeIoCoords(final Bytes[] stack, final Bytes[] preMem) {
    if (stack == null || stack.length < 4) return new IoCoords(0, 0, 0, 0);
    final IoCoords tail = fromTail(stack);
    final IoCoords head = (stack.length >= 4) ? fromHead(stack) : tail;
    return (scoreCoords(head, preMem) > scoreCoords(tail, preMem)) ? head : tail;
  }
}
