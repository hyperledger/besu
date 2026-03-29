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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * Utility class for call tracer operations. Provides methods for converting bytes and extracting
 * data from EVM memory.
 */
public final class CallTracerHelper {
  // Memory constants
  private static final int BYTES_PER_WORD = 32;
  private static final int WORD_SIZE_SHIFT = 5; // For division by 32 (2^5)
  private static final int WORD_MASK = 31; // For modulo 32 (2^5 - 1)

  // Limits
  private static final int MAX_REASONABLE_LENGTH = 1_000_000; // 1MB limit for memory extraction
  private static final BigInteger MAX_INT = BigInteger.valueOf(Integer.MAX_VALUE);

  private CallTracerHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts Bytes to unsigned int, clamped to Integer.MAX_VALUE. Used for extracting stack values
   * that represent offsets and sizes.
   *
   * @param bytes The bytes to convert
   * @return The integer value, or 0 if bytes is null or conversion fails
   */
  public static int bytesToInt(final Bytes bytes) {
    if (bytes == null || bytes.isEmpty()) {
      return 0;
    }

    try {
      final BigInteger value = bytes.toUnsignedBigInteger();
      return value.compareTo(MAX_INT) > 0 ? Integer.MAX_VALUE : value.intValue();
    } catch (final Exception e) {
      // This can happen with malformed bytes
      return 0;
    }
  }

  /**
   * Extracts call data from EVM memory at the specified offset and length. Memory is organized as
   * an array of 32-byte words as exposed by the tracer.
   *
   * <p>This method handles: - Word-aligned memory access - Zero-padding for out-of-bounds access -
   * Protection against unreasonably large memory requests
   *
   * @param memory The memory array (32-byte words)
   * @param offset The byte offset to start extraction
   * @param length The number of bytes to extract
   * @return The extracted bytes, or Bytes.EMPTY if invalid parameters
   */
  public static Bytes extractCallDataFromMemory(
      final Bytes[] memory, final int offset, final int length) {
    // Validate basic parameters
    if (!isValidMemoryRange(offset, length)) {
      return Bytes.EMPTY;
    }

    // Early return for null or empty memory
    if (memory == null || memory.length == 0) {
      // Return zeros for the requested length
      return MutableBytes.create(Math.min(length, MAX_REASONABLE_LENGTH));
    }

    // Calculate word boundaries
    final int startWord = offset >>> WORD_SIZE_SHIFT; // offset / 32
    final int startByteInWord = offset & WORD_MASK; // offset % 32

    // Optimize for single word access
    if (length <= BYTES_PER_WORD - startByteInWord && startWord < memory.length) {
      final Bytes word = getMemoryWord(memory, startWord);
      return word.slice(startByteInWord, length);
    }

    // Multi-word access
    final int endByte = offset + length;
    final int endWord = (endByte + WORD_MASK) >>> WORD_SIZE_SHIFT; // ceil(endByte / 32)

    // Build result directly without intermediate buffer
    final MutableBytes result = MutableBytes.create(length);
    int resultOffset = 0;

    for (int wordIndex = startWord; wordIndex < endWord && resultOffset < length; wordIndex++) {
      final Bytes word = getMemoryWord(memory, wordIndex);

      // Calculate slice boundaries within this word
      final int wordStartByte = (wordIndex == startWord) ? startByteInWord : 0;
      final int wordEndByte = Math.min(BYTES_PER_WORD, wordStartByte + (length - resultOffset));
      final int sliceLength = wordEndByte - wordStartByte;

      if (sliceLength > 0) {
        word.slice(wordStartByte, sliceLength).copyTo(result, resultOffset);
        resultOffset += sliceLength;
      }
    }

    // The remaining bytes (if any) are already zero-initialized in MutableBytes
    return result;
  }

  /** Validates memory access parameters. */
  private static boolean isValidMemoryRange(final int offset, final int length) {
    // Check for negative values
    if (offset < 0 || length <= 0) {
      return false;
    }

    // Protect against unreasonably large allocations
    if (length > MAX_REASONABLE_LENGTH) {
      return false;
    }

    // Check for integer overflow
    try {
      Math.addExact(offset, length);
    } catch (ArithmeticException e) {
      return false;
    }

    return true;
  }

  /** Gets a word from memory or returns zero bytes if out of bounds. */
  private static Bytes getMemoryWord(final Bytes[] memory, final int wordIndex) {
    if (memory == null || wordIndex < 0 || wordIndex >= memory.length) {
      return Bytes32.ZERO;
    }
    final Bytes word = memory[wordIndex];
    return word != null ? word : Bytes32.ZERO;
  }
}
