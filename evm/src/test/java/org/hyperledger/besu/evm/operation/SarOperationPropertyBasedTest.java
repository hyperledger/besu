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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.ArrayDeque;
import java.util.Deque;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Property-based tests comparing original SAR operation with the optimized version.
 *
 * <p>Tests verify that SarOperationOptimized produces identical results to SarOperation for all
 * possible inputs, including edge cases for negative values and sign extension.
 */
public class SarOperationPropertyBasedTest {

  // region Arbitrary Providers

  @Provide
  Arbitrary<byte[]> values1to32() {
    return Arbitraries.bytes().array(byte[].class).ofMinSize(1).ofMaxSize(32);
  }

  @Provide
  Arbitrary<byte[]> shiftAmounts() {
    return Arbitraries.bytes().array(byte[].class).ofMinSize(0).ofMaxSize(32);
  }

  @Provide
  Arbitrary<Integer> smallShifts() {
    return Arbitraries.integers().between(0, 255);
  }

  @Provide
  Arbitrary<Integer> overflowShifts() {
    return Arbitraries.integers().between(256, 1024);
  }

  @Provide
  Arbitrary<byte[]> negativeValues() {
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofSize(32)
        .map(
            bytes -> {
              bytes[0] = (byte) (bytes[0] | 0x80); // set sign bit of 256-bit word
              return bytes;
            });
  }

  @Provide
  Arbitrary<byte[]> positiveValues() {
    // Generate values with sign bit clear (first byte < 0x80)
    return Arbitraries.bytes()
        .array(byte[].class)
        .ofMinSize(1)
        .ofMaxSize(32)
        .map(
            bytes -> {
              if (bytes.length > 0) {
                // Ensure sign bit is clear by ANDing with 0x7F
                bytes[0] = (byte) (bytes[0] & 0x7F);
              }
              return bytes;
            });
  }

  // endregion

  // region SAR Property Tests - Random Inputs

  @Property(tries = 10000)
  void property_sarOptimized_matchesOriginal_randomInputs(
      @ForAll("values1to32") final byte[] valueBytes,
      @ForAll("shiftAmounts") final byte[] shiftBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.wrap(shiftBytes);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as(
            "SAR mismatch for shift=%s, value=%s",
            shift.toHexString(), Bytes32.leftPad(value).toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 5000)
  void property_sarOptimized_matchesOriginal_smallShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("smallShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 1000)
  void property_sarOptimized_matchesOriginal_overflowShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("overflowShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = intToMinimalBytes(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR overflow mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region SAR Property Tests - Negative Values (Sign Extension)

  @Property(tries = 5000)
  void property_sarOptimized_matchesOriginal_negativeValues_smallShifts(
      @ForAll("negativeValues") final byte[] valueBytes, @ForAll("smallShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR negative mismatch for shift=%d, value=%s", shift, value.toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 1000)
  void property_sarOptimized_matchesOriginal_negativeValues_overflowShifts(
      @ForAll("negativeValues") final byte[] valueBytes,
      @ForAll("overflowShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = intToMinimalBytes(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    // For negative values with overflow shift, result should be all ones
    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR negative overflow mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region SAR Property Tests - Positive Values

  @Property(tries = 5000)
  void property_sarOptimized_matchesOriginal_positiveValues_smallShifts(
      @ForAll("positiveValues") final byte[] valueBytes, @ForAll("smallShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR positive mismatch for shift=%d, value=%s", shift, value.toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 1000)
  void property_sarOptimized_matchesOriginal_positiveValues_overflowShifts(
      @ForAll("positiveValues") final byte[] valueBytes,
      @ForAll("overflowShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = intToMinimalBytes(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    // For positive values with overflow shift, result should be all zeros
    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR positive overflow mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region Edge Case Tests

  @Property(tries = 1000)
  void property_sar_shiftByZero_returnsOriginalValue(
      @ForAll("values1to32") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.of(0);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.leftPad(value));
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.leftPad(value));
  }

  @Property(tries = 500)
  void property_sar_negativeValue_largeShift_returnsAllOnes(
      @ForAll("negativeValues") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes largeShift = Bytes.fromHexString("0x010000000000");

    final Bytes originalResult = runSarOperation(largeShift, value);
    final Bytes optimizedResult = runSarOperationOptimized(largeShift, value);

    // Both should return all ones for negative value with large shift
    final Bytes32 allOnes =
        Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(allOnes);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(allOnes);
  }

  @Property(tries = 500)
  void property_sar_positiveValue_largeShift_returnsZero(
      @ForAll("positiveValues") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes largeShift = Bytes.fromHexString("0x010000000000");

    final Bytes originalResult = runSarOperation(largeShift, value);
    final Bytes optimizedResult = runSarOperationOptimized(largeShift, value);

    // Both should return zero for positive value with large shift
    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.ZERO);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.ZERO);
  }

  @Property(tries = 500)
  void property_sar_allOnes_anyShift_returnsAllOnes(@ForAll("smallShifts") final int shift) {

    // -1 in two's complement (all bits set)
    final Bytes value =
        Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runSarOperation(shiftBytes, value);
    final Bytes optimizedResult = runSarOperationOptimized(shiftBytes, value);

    // SAR of -1 by any amount should still be -1 (all ones)
    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.leftPad(value));
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.leftPad(value));
  }

  @Property(tries = 500)
  void property_sar_minValue_shift255_returnsAllOnes() {

    // MIN_VALUE: 0x8000...0000 (only sign bit set)
    final Bytes value =
        Bytes.fromHexString("0x8000000000000000000000000000000000000000000000000000000000000000");
    final Bytes shift = Bytes.of(255);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    // SAR of MIN_VALUE by 255 should be all ones (-1)
    final Bytes32 allOnes =
        Bytes32.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(allOnes);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(allOnes);
  }

  @Property(tries = 500)
  void property_sar_maxPositive_shift255_returnsZero() {

    // MAX_VALUE: 0x7fff...ffff (all bits except sign bit set)
    final Bytes value =
        Bytes.fromHexString("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    final Bytes shift = Bytes.of(255);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    // SAR of MAX_VALUE by 255 should be 0
    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.ZERO);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.ZERO);
  }

  @Property(tries = 3000)
  void property_sarOptimized_matchesOriginal_negativeValues_shift255(
      @ForAll("negativeValues") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.of(255);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR negative shift=255 mismatch for value=%s", value.toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 3000)
  void property_sarOptimized_matchesOriginal_positiveValues_shift255(
      @ForAll("positiveValues") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.of(255);

    final Bytes originalResult = runSarOperation(shift, value);
    final Bytes optimizedResult = runSarOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SAR positive shift=255 mismatch for value=%s", value.toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region Helper Methods

  private Bytes runSarOperation(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, SarOperation::staticOperation);
  }

  private Bytes runSarOperationOptimized(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, SarOperationOptimized::staticOperation);
  }

  @FunctionalInterface
  interface OperationExecutor {
    Operation.OperationResult execute(MessageFrame frame);
  }

  private Bytes runOperation(
      final Bytes shift, final Bytes value, final OperationExecutor executor) {
    final MessageFrame frame = mock(MessageFrame.class);
    final Deque<Bytes> stack = new ArrayDeque<>();
    stack.push(value);
    stack.push(shift);

    when(frame.popStackItem()).thenAnswer(invocation -> stack.pop());

    final Bytes[] result = new Bytes[1];
    doAnswer(
            invocation -> {
              result[0] = invocation.getArgument(0);
              return null;
            })
        .when(frame)
        .pushStackItem(any(Bytes.class));

    executor.execute(frame);
    return result[0];
  }

  private Bytes intToMinimalBytes(final int value) {
    if (value == 0) {
      return Bytes.EMPTY;
    }
    if (value <= 0xFF) {
      return Bytes.of(value);
    }
    if (value <= 0xFFFF) {
      return Bytes.of(value >> 8, value & 0xFF);
    }
    if (value <= 0xFFFFFF) {
      return Bytes.of(value >> 16, (value >> 8) & 0xFF, value & 0xFF);
    }
    return Bytes.of(value >> 24, (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
  }

  // endregion
}
