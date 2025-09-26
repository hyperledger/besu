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

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;

/**
 * Utility class for call tracer operations. Provides methods for converting bytes and extracting
 * data from EVM memory.
 */
final class CallTracerHelper {
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
  static int bytesToInt(final Bytes bytes) {
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
  static Bytes extractCallDataFromMemory(final Bytes[] memory, final int offset, final int length) {
    // Validate basic parameters
    if (!isValidMemoryRange(offset, length)) {
      return Bytes.EMPTY;
    }

    // Calculate word boundaries
    final int startWord = offset >>> WORD_SIZE_SHIFT; // offset / 32
    final int endWord =
        (offset + length + WORD_MASK) >>> WORD_SIZE_SHIFT; // ceil((offset + length) / 32)
    final int wordCount = endWord - startWord;

    if (wordCount <= 0) {
      return Bytes.EMPTY;
    }

    // Build the memory slice
    final MutableBytes buffer = buildMemoryBuffer(memory, startWord, wordCount);

    // Extract the requested slice
    return extractSlice(buffer, offset & WORD_MASK, length);
  }

  /** Validates memory access parameters. */
  private static boolean isValidMemoryRange(final int offset, final int length) {
    if (offset < 0 || length <= 0) {
      return false;
    }

    // Protect against unreasonably large allocations (common in failed calls)
    if (length > MAX_REASONABLE_LENGTH) {
      return false;
    }

    // Check for integer overflow
    if (offset > Integer.MAX_VALUE - length) {
      return false;
    }

    return true;
  }

  /** Builds a buffer containing the memory words needed for extraction. */
  private static MutableBytes buildMemoryBuffer(
      final Bytes[] memory, final int startWord, final int wordCount) {
    final MutableBytes buffer = MutableBytes.create(wordCount * BYTES_PER_WORD);

    for (int i = 0; i < wordCount; i++) {
      final int wordIndex = startWord + i;
      final Bytes word = getMemoryWord(memory, wordIndex);
      word.copyTo(buffer, i * BYTES_PER_WORD);
    }

    return buffer;
  }

  /** Gets a word from memory or returns zero bytes if out of bounds. */
  private static Bytes getMemoryWord(final Bytes[] memory, final int wordIndex) {
    if (memory == null || wordIndex < 0 || wordIndex >= memory.length) {
      return Bytes32.ZERO;
    }

    final Bytes word = memory[wordIndex];
    return word != null ? word : Bytes32.ZERO;
  }

  /** Extracts the final slice from the buffer with proper bounds checking. */
  private static Bytes extractSlice(
      final MutableBytes buffer, final int startByteInWord, final int length) {
    // Simple case: entire slice fits within buffer
    if (startByteInWord + length <= buffer.size()) {
      return buffer.slice(startByteInWord, length);
    }

    // Complex case: need to handle partial slice and zero-padding
    final int availableBytes = buffer.size() - startByteInWord;
    if (availableBytes <= 0) {
      // Start is beyond buffer, return zeros
      return MutableBytes.create(length);
    }

    // Extract what we can and pad the rest with zeros
    final Bytes availableSlice = buffer.slice(startByteInWord, availableBytes);
    final int paddingNeeded = length - availableBytes;

    if (paddingNeeded <= 0) {
      return availableSlice;
    }

    return Bytes.concatenate(availableSlice, MutableBytes.create(paddingNeeded));
  }
}
