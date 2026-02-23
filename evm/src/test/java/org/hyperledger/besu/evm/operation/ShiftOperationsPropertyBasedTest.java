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
 * Property-based tests comparing original shift operations with their optimized versions.
 *
 * <p>Tests verify that SHL/SHR optimized implementations produce identical results to the original
 * implementations for all possible inputs.
 */
public class ShiftOperationsPropertyBasedTest {

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

  // endregion

  // region SHL Property Tests

  @Property(tries = 10000)
  void property_shlOptimized_matchesOriginal_randomInputs(
      @ForAll("values1to32") final byte[] valueBytes,
      @ForAll("shiftAmounts") final byte[] shiftBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.wrap(shiftBytes);

    final Bytes originalResult = runShlOperation(shift, value);
    final Bytes optimizedResult = runShlOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as(
            "SHL mismatch for shift=%s, value=%s",
            shift.toHexString(), Bytes32.leftPad(value).toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 5000)
  void property_shlOptimized_matchesOriginal_smallShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("smallShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runShlOperation(shiftBytes, value);
    final Bytes optimizedResult = runShlOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SHL mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 1000)
  void property_shlOptimized_matchesOriginal_overflowShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("overflowShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = intToMinimalBytes(shift);

    final Bytes originalResult = runShlOperation(shiftBytes, value);
    final Bytes optimizedResult = runShlOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SHL overflow mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region SHR Property Tests

  @Property(tries = 10000)
  void property_shrOptimized_matchesOriginal_randomInputs(
      @ForAll("values1to32") final byte[] valueBytes,
      @ForAll("shiftAmounts") final byte[] shiftBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.wrap(shiftBytes);

    final Bytes originalResult = runShrOperation(shift, value);
    final Bytes optimizedResult = runShrOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as(
            "SHR mismatch for shift=%s, value=%s",
            shift.toHexString(), Bytes32.leftPad(value).toHexString())
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 5000)
  void property_shrOptimized_matchesOriginal_smallShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("smallShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = Bytes.of(shift);

    final Bytes originalResult = runShrOperation(shiftBytes, value);
    final Bytes optimizedResult = runShrOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SHR mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  @Property(tries = 1000)
  void property_shrOptimized_matchesOriginal_overflowShifts(
      @ForAll("values1to32") final byte[] valueBytes, @ForAll("overflowShifts") final int shift) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shiftBytes = intToMinimalBytes(shift);

    final Bytes originalResult = runShrOperation(shiftBytes, value);
    final Bytes optimizedResult = runShrOperationOptimized(shiftBytes, value);

    assertThat(Bytes32.leftPad(optimizedResult))
        .as("SHR overflow mismatch for shift=%d", shift)
        .isEqualTo(Bytes32.leftPad(originalResult));
  }

  // endregion

  // region Edge Case Tests

  @Property(tries = 1000)
  void property_shl_shiftByZero_returnsOriginalValue(
      @ForAll("values1to32") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.of(0);

    final Bytes originalResult = runShlOperation(shift, value);
    final Bytes optimizedResult = runShlOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.leftPad(value));
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.leftPad(value));
  }

  @Property(tries = 1000)
  void property_shr_shiftByZero_returnsOriginalValue(
      @ForAll("values1to32") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes shift = Bytes.of(0);

    final Bytes originalResult = runShrOperation(shift, value);
    final Bytes optimizedResult = runShrOperationOptimized(shift, value);

    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.leftPad(value));
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.leftPad(value));
  }

  @Property(tries = 500)
  void property_shl_largeShift_returnsZero(@ForAll("values1to32") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes largeShift = Bytes.fromHexString("0x010000000000");

    final Bytes originalResult = runShlOperation(largeShift, value);
    final Bytes optimizedResult = runShlOperationOptimized(largeShift, value);

    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.ZERO);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.ZERO);
  }

  @Property(tries = 500)
  void property_shr_largeShift_returnsZero(@ForAll("values1to32") final byte[] valueBytes) {

    final Bytes value = Bytes.wrap(valueBytes);
    final Bytes largeShift = Bytes.fromHexString("0x010000000000");

    final Bytes originalResult = runShrOperation(largeShift, value);
    final Bytes optimizedResult = runShrOperationOptimized(largeShift, value);

    assertThat(Bytes32.leftPad(optimizedResult)).isEqualTo(Bytes32.ZERO);
    assertThat(Bytes32.leftPad(originalResult)).isEqualTo(Bytes32.ZERO);
  }

  // endregion

  // region Helper Methods

  private Bytes runShlOperation(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, ShlOperation::staticOperation);
  }

  private Bytes runShlOperationOptimized(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, ShlOperationOptimized::staticOperation);
  }

  private Bytes runShrOperation(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, ShrOperation::staticOperation);
  }

  private Bytes runShrOperationOptimized(final Bytes shift, final Bytes value) {
    return runOperation(shift, value, ShrOperationOptimized::staticOperation);
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
