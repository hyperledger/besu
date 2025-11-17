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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class CallTracerHelperTest {

  // ==================== BytesToInt Tests ====================

  @Test
  @DisplayName("bytesToInt - Should return 0 for null input")
  void bytesToInt_shouldReturnZeroForNull() {
    assertThat(CallTracerHelper.bytesToInt(null)).isEqualTo(0);
  }

  @Test
  @DisplayName("bytesToInt - Should return 0 for empty bytes")
  void bytesToInt_shouldReturnZeroForEmptyBytes() {
    assertThat(CallTracerHelper.bytesToInt(Bytes.EMPTY)).isEqualTo(0);
  }

  @Test
  @DisplayName("bytesToInt - Should convert single byte values correctly")
  void bytesToInt_shouldConvertSingleByte() {
    assertThat(CallTracerHelper.bytesToInt(Bytes.of(0))).isEqualTo(0);
    assertThat(CallTracerHelper.bytesToInt(Bytes.of(1))).isEqualTo(1);
    assertThat(CallTracerHelper.bytesToInt(Bytes.of(127))).isEqualTo(127);
    assertThat(CallTracerHelper.bytesToInt(Bytes.of((byte) 0xFF))).isEqualTo(255);
  }

  @Test
  @DisplayName("bytesToInt - Should convert multi-byte values correctly")
  void bytesToInt_shouldConvertMultiByteValues() {
    // 256 = 0x0100
    assertThat(CallTracerHelper.bytesToInt(Bytes.fromHexString("0x0100"))).isEqualTo(256);
    // 65535 = 0xFFFF
    assertThat(CallTracerHelper.bytesToInt(Bytes.fromHexString("0xFFFF"))).isEqualTo(65535);
    // 16777215 = 0xFFFFFF
    assertThat(CallTracerHelper.bytesToInt(Bytes.fromHexString("0xFFFFFF"))).isEqualTo(16777215);
  }

  @Test
  @DisplayName("bytesToInt - Should clamp values larger than Integer.MAX_VALUE")
  void bytesToInt_shouldClampLargeValues() {
    // Integer.MAX_VALUE + 1
    Bytes largeValue = Bytes.fromHexString("0x80000000");
    assertThat(CallTracerHelper.bytesToInt(largeValue)).isEqualTo(Integer.MAX_VALUE);

    // Much larger value
    Bytes veryLargeValue = Bytes.fromHexString("0xFFFFFFFFFFFFFFFF");
    assertThat(CallTracerHelper.bytesToInt(veryLargeValue)).isEqualTo(Integer.MAX_VALUE);

    // 32 bytes of 0xFF
    Bytes32 maxBytes32 =
        Bytes32.fromHexString("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    assertThat(CallTracerHelper.bytesToInt(maxBytes32)).isEqualTo(Integer.MAX_VALUE);
  }

  @ParameterizedTest
  @CsvSource({
    "0x00, 0",
    "0x01, 1",
    "0xFF, 255",
    "0x0100, 256",
    "0xFFFF, 65535",
    "0x010000, 65536",
    "0x7FFFFFFF, 2147483647" // Integer.MAX_VALUE
  })
  @DisplayName("bytesToInt - Should convert hex values correctly")
  void bytesToInt_parameterizedHexConversion(final String hex, final int expected) {
    assertThat(CallTracerHelper.bytesToInt(Bytes.fromHexString(hex))).isEqualTo(expected);
  }

  // ==================== ExtractCallDataFromMemory Tests ====================

  @Test
  @DisplayName("extractCallData - Should return empty bytes for null memory")
  void extractCallData_shouldReturnEmptyForNullMemory() {
    Bytes result = CallTracerHelper.extractCallDataFromMemory(null, 0, 10);
    assertThat(result).isEqualTo(MutableBytes.create(10));
  }

  @Test
  @DisplayName("extractCallData - Should return empty bytes for empty memory array")
  void extractCallData_shouldReturnEmptyForEmptyMemoryArray() {
    Bytes[] emptyMemory = new Bytes[0];
    Bytes result = CallTracerHelper.extractCallDataFromMemory(emptyMemory, 0, 10);
    assertThat(result).isEqualTo(MutableBytes.create(10));
  }

  @Test
  @DisplayName("extractCallData - Should return empty bytes for negative offset")
  void extractCallData_shouldReturnEmptyForNegativeOffset() {
    Bytes[] memory = createMemoryWithData();
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, -1, 10);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  @DisplayName("extractCallData - Should return empty bytes for zero length")
  void extractCallData_shouldReturnEmptyForZeroLength() {
    Bytes[] memory = createMemoryWithData();
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 0);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  @DisplayName("extractCallData - Should return empty bytes for negative length")
  void extractCallData_shouldReturnEmptyForNegativeLength() {
    Bytes[] memory = createMemoryWithData();
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, -1);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  @DisplayName(
      "extractCallData - Should return empty bytes for length exceeding max reasonable length")
  void extractCallData_shouldReturnEmptyForExcessiveLength() {
    Bytes[] memory = createMemoryWithData();
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 1_000_001);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  @DisplayName("extractCallData - Should handle integer overflow in offset + length")
  void extractCallData_shouldHandleIntegerOverflow() {
    Bytes[] memory = createMemoryWithData();
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, Integer.MAX_VALUE - 5, 10);
    assertThat(result).isEqualTo(Bytes.EMPTY);
  }

  @Test
  @DisplayName("extractCallData - Should extract data from single word")
  void extractCallData_shouldExtractFromSingleWord() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
        };

    // Extract first 4 bytes
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 4);
    assertThat(result).isEqualTo(Bytes.fromHexString("0x01020304"));

    // Extract middle 4 bytes
    result = CallTracerHelper.extractCallDataFromMemory(memory, 10, 4);
    assertThat(result).isEqualTo(Bytes.fromHexString("0x0b0c0d0e"));

    // Extract last 4 bytes
    result = CallTracerHelper.extractCallDataFromMemory(memory, 28, 4);
    assertThat(result).isEqualTo(Bytes.fromHexString("0x1d1e1f20"));
  }

  @Test
  @DisplayName("extractCallData - Should extract data spanning multiple words")
  void extractCallData_shouldExtractAcrossMultipleWords() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0x0000000000000000000000000000000000000000000000000000000000000001"),
          Bytes32.fromHexString(
              "0x0000000000000000000000000000000000000000000000000000000000000002"),
          Bytes32.fromHexString(
              "0x0000000000000000000000000000000000000000000000000000000000000003")
        };

    // Extract across word boundary (last 4 bytes of word 0 and first 4 bytes of word 1)
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 28, 8);
    assertThat(result).isEqualTo(Bytes.fromHexString("0x0000000100000000"));

    // Extract across all three words
    result = CallTracerHelper.extractCallDataFromMemory(memory, 30, 36);
    assertThat(result.size()).isEqualTo(36);
  }

  @Test
  @DisplayName("extractCallData - Should handle extraction beyond memory bounds with zero padding")
  void extractCallData_shouldHandleOutOfBoundsWithZeroPadding() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0x1111111111111111111111111111111111111111111111111111111111111111")
        };

    // Request data beyond available memory
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 30, 10);
    assertThat(result.size()).isEqualTo(10);
    // First 2 bytes from memory
    assertThat(result.slice(0, 2)).isEqualTo(Bytes.fromHexString("0x1111"));
    // Remaining 8 bytes should be zeros
    assertThat(result.slice(2, 8)).isEqualTo(MutableBytes.create(8));
  }

  @Test
  @DisplayName("extractCallData - Should handle null words in memory array")
  void extractCallData_shouldHandleNullWordsInMemory() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0x1111111111111111111111111111111111111111111111111111111111111111"),
          null, // null word should be treated as zeros
          Bytes32.fromHexString(
              "0x3333333333333333333333333333333333333333333333333333333333333333")
        };

    // Extract across the null word
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 30, 36);
    assertThat(result.size()).isEqualTo(36);
    // Check that null word is treated as zeros
    assertThat(result.slice(2, 32)).isEqualTo(Bytes32.ZERO);
  }

  @Test
  @DisplayName("extractCallData - Should optimize single word access within bounds")
  void extractCallData_shouldOptimizeSingleWordAccess() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
          Bytes32.fromHexString(
              "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
        };

    // Access that fits within single word
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 5, 20);
    assertThat(result.size()).isEqualTo(20);
    assertThat(result).isEqualTo(Bytes.fromHexString("0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
  }

  @ParameterizedTest
  @CsvSource({
    "0, 1, 1", // Single byte at start
    "0, 32, 32", // Full word
    "31, 1, 1", // Single byte at end of word
    "30, 4, 4", // Crossing word boundary
    "0, 64, 64", // Two full words
    "16, 32, 32", // Middle of first word to middle of second
    "0, 96, 96" // Three full words
  })
  @DisplayName(
      "extractCallData - Should extract correct size for various offset/length combinations")
  void extractCallData_parameterizedSizeTests(
      final int offset, final int length, final int expectedSize) {
    Bytes[] memory = createLargeMemory(10); // 10 words
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, offset, length);
    assertThat(result.size()).isEqualTo(expectedSize);
  }

  @Test
  @DisplayName("extractCallData - Should handle edge case of offset at word boundary")
  void extractCallData_shouldHandleOffsetAtWordBoundary() {
    Bytes[] memory =
        new Bytes[] {
          Bytes32.fromHexString(
              "0x1111111111111111111111111111111111111111111111111111111111111111"),
          Bytes32.fromHexString(
              "0x2222222222222222222222222222222222222222222222222222222222222222")
        };

    // Offset exactly at word boundary
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 32, 32);
    assertThat(result)
        .isEqualTo(
            Bytes32.fromHexString(
                "0x2222222222222222222222222222222222222222222222222222222222222222"));
  }

  @Test
  @DisplayName("extractCallData - Should handle maximum reasonable length")
  void extractCallData_shouldHandleMaxReasonableLength() {
    Bytes[] memory = createLargeMemory(100); // Create sufficient memory (100 words = 3200 bytes)

    // Test with maximum allowed length (1MB) - should return empty because it exceeds the limit
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 1_000_001);
    assertThat(result)
        .isEqualTo(Bytes.EMPTY); // Should return empty for length > MAX_REASONABLE_LENGTH

    // Test with exactly 1MB (the maximum allowed)
    result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 1_000_000);
    // Since we only have 3200 bytes of memory, it should return 3200 bytes from memory
    // followed by zeros for the rest
    assertThat(result.size()).isEqualTo(1_000_000);

    // Verify first 3200 bytes match the memory content
    for (int i = 0; i < 100; i++) {
      byte expectedByte = (byte) (i & 0xFF);
      Bytes wordSlice = result.slice(i * 32, 32);
      // Each word should be filled with the same byte value
      for (int j = 0; j < 32; j++) {
        assertThat(wordSlice.get(j)).isEqualTo(expectedByte);
      }
    }

    // Verify remaining bytes are zeros (from byte 3200 to 1_000_000)
    Bytes remainingBytes = result.slice(3200, 1_000_000 - 3200);
    assertThat(remainingBytes).isEqualTo(MutableBytes.create(1_000_000 - 3200));

    // Test with just the available memory size
    result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 3200); // 100 words * 32 bytes
    assertThat(result.size()).isEqualTo(3200);

    // Verify all bytes match the memory content
    for (int i = 0; i < 100; i++) {
      byte expectedByte = (byte) (i & 0xFF);
      Bytes wordSlice = result.slice(i * 32, 32);
      for (int j = 0; j < 32; j++) {
        assertThat(wordSlice.get(j)).isEqualTo(expectedByte);
      }
    }
  }

  @ParameterizedTest
  @MethodSource("provideMemoryExtractionScenarios")
  @DisplayName("extractCallData - Complex extraction scenarios")
  void extractCallData_complexScenarios(
      final String description,
      final Bytes[] memory,
      final int offset,
      final int length,
      final Bytes expected) {
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, offset, length);
    assertThat(result).as(description).isEqualTo(expected);
  }

  @Test
  @DisplayName("extractCallData - Should handle sparse memory arrays")
  void extractCallData_shouldHandleSparseMemory() {
    Bytes[] memory = new Bytes[10];
    memory[0] =
        Bytes32.fromHexString("0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    memory[5] =
        Bytes32.fromHexString("0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
    // Other elements are null

    // Extract across null elements
    Bytes result = CallTracerHelper.extractCallDataFromMemory(memory, 0, 192); // 6 words
    assertThat(result.size()).isEqualTo(192);

    // Verify first word
    assertThat(result.slice(0, 32)).isEqualTo(memory[0]);
    // Verify null words are zeros
    for (int i = 1; i < 5; i++) {
      assertThat(result.slice(i * 32, 32)).isEqualTo(Bytes32.ZERO);
    }
    // Verify sixth word
    assertThat(result.slice(160, 32)).isEqualTo(memory[5]);
  }

  @Test
  @DisplayName("extractCallData - Performance test with large memory")
  void extractCallData_performanceWithLargeMemory() {
    // Create a large memory array (1000 words = 32KB)
    Bytes[] memory = createLargeMemory(1000);

    long startTime = System.nanoTime();

    // Extract various chunks
    for (int i = 0; i < 100; i++) {
      CallTracerHelper.extractCallDataFromMemory(memory, i * 32, 256);
    }

    long duration = System.nanoTime() - startTime;

    // Should complete reasonably fast (< 100ms for 100 operations)
    assertThat(duration).isLessThan(100_000_000L);
  }

  // ==================== Helper Methods ====================

  private static Bytes[] createMemoryWithData() {
    return new Bytes[] {
      Bytes32.fromHexString("0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20"),
      Bytes32.fromHexString("0x2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40"),
      Bytes32.fromHexString("0x4142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f60")
    };
  }

  private static Bytes[] createLargeMemory(final int wordCount) {
    Bytes[] memory = new Bytes[wordCount];
    for (int i = 0; i < wordCount; i++) {
      MutableBytes word = MutableBytes.create(32);
      word.fill((byte) (i & 0xFF));
      memory[i] = word;
    }
    return memory;
  }

  private static Stream<Arguments> provideMemoryExtractionScenarios() {
    return Stream.of(
        Arguments.of("Extract from empty memory", new Bytes[0], 0, 32, MutableBytes.create(32)),
        Arguments.of(
            "Extract single byte from start",
            new Bytes[] {
              Bytes32.fromHexString(
                  "0xFF00000000000000000000000000000000000000000000000000000000000000")
            },
            0,
            1,
            Bytes.fromHexString("0xFF")),
        Arguments.of(
            "Extract with offset beyond memory",
            new Bytes[] {Bytes32.ZERO},
            100,
            10,
            MutableBytes.create(10)),
        Arguments.of(
            "Extract exact word",
            new Bytes[] {
              Bytes32.fromHexString(
                  "0x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
            },
            0,
            32,
            Bytes32.fromHexString(
                "0x0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")),
        Arguments.of(
            "Extract with unaligned offset and length",
            new Bytes[] {
              Bytes32.fromHexString(
                  "0x1111111111111111111111111111111111111111111111111111111111111111"),
              Bytes32.fromHexString(
                  "0x2222222222222222222222222222222222222222222222222222222222222222")
            },
            13,
            27,
            Bytes.fromHexString("0x111111111111111111111111111111111111112222222222222222")));
  }
}
