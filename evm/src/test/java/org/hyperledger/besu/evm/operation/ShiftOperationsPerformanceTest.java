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
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.io.PrintStream;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Performance test comparing shift operations (SHL/SHR) with their optimized versions.
 *
 * <p>Measures throughput in Mgas/s (million gas per second). All shift operations cost 3 gas.
 */
public class ShiftOperationsPerformanceTest {

  private static final PrintStream OUT = System.out;
  private static final long SHIFT_GAS_COST = 3L;
  private static final int WARMUP_ITERATIONS = 10_000;
  private static final int MEASURE_ITERATIONS = 100_000;
  private static final int SAMPLE_SIZE = 10_000;

  private MessageFrame frame;
  private Bytes[] valuePool;
  private Bytes[] shiftPool;
  private Random random;

  @BeforeEach
  void setUp() {
    frame = createMessageFrame();
    random = new Random(42);
    valuePool = new Bytes[SAMPLE_SIZE];
    shiftPool = new Bytes[SAMPLE_SIZE];
  }

  // region SHL Performance Tests

  @Test
  void measureShlPerformance_smallShifts() {
    OUT.println("\n=== SHL Performance: Small Shifts (0-255) ===");
    fillPoolsSmallShifts();
    runShlComparison("SHL_SMALL_SHIFTS");
  }

  @Test
  void measureShlPerformance_shift1() {
    OUT.println("\n=== SHL Performance: Shift by 1 ===");
    fillPoolsShift1();
    runShlComparison("SHL_SHIFT_1");
  }

  @Test
  void measureShlPerformance_overflowShifts() {
    OUT.println("\n=== SHL Performance: Overflow Shifts (>= 256) ===");
    fillPoolsOverflowShifts();
    runShlComparison("SHL_OVERFLOW");
  }

  @Test
  void measureShlPerformance_random() {
    OUT.println("\n=== SHL Performance: Random Inputs ===");
    fillPoolsRandom();
    runShlComparison("SHL_RANDOM");
  }

  // endregion

  // region SHR Performance Tests

  @Test
  void measureShrPerformance_smallShifts() {
    OUT.println("\n=== SHR Performance: Small Shifts (0-255) ===");
    fillPoolsSmallShifts();
    runShrComparison("SHR_SMALL_SHIFTS");
  }

  @Test
  void measureShrPerformance_shift1() {
    OUT.println("\n=== SHR Performance: Shift by 1 ===");
    fillPoolsShift1();
    runShrComparison("SHR_SHIFT_1");
  }

  @Test
  void measureShrPerformance_overflowShifts() {
    OUT.println("\n=== SHR Performance: Overflow Shifts (>= 256) ===");
    fillPoolsOverflowShifts();
    runShrComparison("SHR_OVERFLOW");
  }

  @Test
  void measureShrPerformance_random() {
    OUT.println("\n=== SHR Performance: Random Inputs ===");
    fillPoolsRandom();
    runShrComparison("SHR_RANDOM");
  }

  // endregion

  // region Pool Initialization

  private void fillPoolsSmallShifts() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      byte[] valueBytes = new byte[32];
      random.nextBytes(valueBytes);
      valuePool[i] = Bytes.wrap(valueBytes);
      shiftPool[i] = Bytes.of(random.nextInt(256));
    }
  }

  private void fillPoolsShift1() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      byte[] valueBytes = new byte[32];
      random.nextBytes(valueBytes);
      valuePool[i] = Bytes.wrap(valueBytes);
      shiftPool[i] = Bytes.of(1);
    }
  }

  private void fillPoolsOverflowShifts() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      byte[] valueBytes = new byte[32];
      random.nextBytes(valueBytes);
      valuePool[i] = Bytes.wrap(valueBytes);
      int shift = 256 + random.nextInt(1000);
      shiftPool[i] = intToMinimalBytes(shift);
    }
  }

  private void fillPoolsRandom() {
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      int valueSize = 1 + random.nextInt(32);
      byte[] valueBytes = new byte[valueSize];
      random.nextBytes(valueBytes);
      valuePool[i] = Bytes.wrap(valueBytes);

      int shiftSize = random.nextInt(5);
      if (shiftSize == 0) {
        shiftPool[i] = Bytes.EMPTY;
      } else {
        byte[] shiftBytes = new byte[shiftSize];
        random.nextBytes(shiftBytes);
        shiftPool[i] = Bytes.wrap(shiftBytes);
      }
    }
  }

  // endregion

  // region Performance Measurement

  private void runShlComparison(final String testName) {
    OUT.println("Warming up...");
    warmupShl(false);
    warmupShl(true);

    OUT.println("Measuring original ShlOperation...");
    PerformanceResult original = measureShl(false);

    OUT.println("Measuring optimized ShlOperationOptimized...");
    PerformanceResult optimized = measureShl(true);

    printResults(testName, original, optimized);
    verifyShlCorrectness();
  }

  private void runShrComparison(final String testName) {
    OUT.println("Warming up...");
    warmupShr(false);
    warmupShr(true);

    OUT.println("Measuring original ShrOperation...");
    PerformanceResult original = measureShr(false);

    OUT.println("Measuring optimized ShrOperationOptimized...");
    PerformanceResult optimized = measureShr(true);

    printResults(testName, original, optimized);
    verifyShrCorrectness();
  }

  private void warmupShl(final boolean useOptimized) {
    int index = 0;
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      frame.pushStackItem(valuePool[index]);
      frame.pushStackItem(shiftPool[index]);
      if (useOptimized) {
        ShlOperationOptimized.staticOperation(frame);
      } else {
        ShlOperation.staticOperation(frame);
      }
      frame.popStackItem();
      index = (index + 1) % SAMPLE_SIZE;
    }
  }

  private void warmupShr(final boolean useOptimized) {
    int index = 0;
    for (int i = 0; i < WARMUP_ITERATIONS; i++) {
      frame.pushStackItem(valuePool[index]);
      frame.pushStackItem(shiftPool[index]);
      if (useOptimized) {
        ShrOperationOptimized.staticOperation(frame);
      } else {
        ShrOperation.staticOperation(frame);
      }
      frame.popStackItem();
      index = (index + 1) % SAMPLE_SIZE;
    }
  }

  private PerformanceResult measureShl(final boolean useOptimized) {
    int index = 0;
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      frame.pushStackItem(valuePool[index]);
      frame.pushStackItem(shiftPool[index]);
      if (useOptimized) {
        ShlOperationOptimized.staticOperation(frame);
      } else {
        ShlOperation.staticOperation(frame);
      }
      frame.popStackItem();
      index = (index + 1) % SAMPLE_SIZE;
    }
    long endTime = System.nanoTime();
    return new PerformanceResult(MEASURE_ITERATIONS, endTime - startTime, SHIFT_GAS_COST);
  }

  private PerformanceResult measureShr(final boolean useOptimized) {
    int index = 0;
    long startTime = System.nanoTime();
    for (int i = 0; i < MEASURE_ITERATIONS; i++) {
      frame.pushStackItem(valuePool[index]);
      frame.pushStackItem(shiftPool[index]);
      if (useOptimized) {
        ShrOperationOptimized.staticOperation(frame);
      } else {
        ShrOperation.staticOperation(frame);
      }
      frame.popStackItem();
      index = (index + 1) % SAMPLE_SIZE;
    }
    long endTime = System.nanoTime();
    return new PerformanceResult(MEASURE_ITERATIONS, endTime - startTime, SHIFT_GAS_COST);
  }

  private void verifyShlCorrectness() {
    OUT.println("Verifying SHL correctness...");
    int sampleCount = Math.min(1000, SAMPLE_SIZE);
    for (int i = 0; i < sampleCount; i++) {
      Bytes value = valuePool[i];
      Bytes shift = shiftPool[i];

      frame.pushStackItem(value);
      frame.pushStackItem(shift);
      ShlOperation.staticOperation(frame);
      Bytes originalResult = frame.popStackItem();

      frame.pushStackItem(value);
      frame.pushStackItem(shift);
      ShlOperationOptimized.staticOperation(frame);
      Bytes optimizedResult = frame.popStackItem();

      assertThat(Bytes32.leftPad(optimizedResult))
          .as("SHL mismatch at index %d", i)
          .isEqualTo(Bytes32.leftPad(originalResult));
    }
    OUT.println("SHL correctness verified for " + sampleCount + " samples");
  }

  private void verifyShrCorrectness() {
    OUT.println("Verifying SHR correctness...");
    int sampleCount = Math.min(1000, SAMPLE_SIZE);
    for (int i = 0; i < sampleCount; i++) {
      Bytes value = valuePool[i];
      Bytes shift = shiftPool[i];

      frame.pushStackItem(value);
      frame.pushStackItem(shift);
      ShrOperation.staticOperation(frame);
      Bytes originalResult = frame.popStackItem();

      frame.pushStackItem(value);
      frame.pushStackItem(shift);
      ShrOperationOptimized.staticOperation(frame);
      Bytes optimizedResult = frame.popStackItem();

      assertThat(Bytes32.leftPad(optimizedResult))
          .as("SHR mismatch at index %d", i)
          .isEqualTo(Bytes32.leftPad(originalResult));
    }
    OUT.println("SHR correctness verified for " + sampleCount + " samples");
  }

  private void printResults(
      final String testName,
      final PerformanceResult original,
      final PerformanceResult optimized) {

    double speedup = original.nsPerOp() / optimized.nsPerOp();
    double percentFaster = (1 - optimized.nsPerOp() / original.nsPerOp()) * 100;

    OUT.println();
    OUT.println("╔═══════════════════════════════════════════════════════════════════╗");
    OUT.println("║ Shift Operation Performance Results: " + testName);
    OUT.println("╠═══════════════════════════════════════════════════════════════════╣");
    OUT.println("║ Original Operation:");
    OUT.printf("║   - Operations:     %,d%n", original.operations);
    OUT.printf("║   - Total time:     %.2f ms%n", original.elapsedMs());
    OUT.printf("║   - Throughput:     %,.0f ops/sec%n", original.opsPerSecond());
    OUT.printf("║   - Latency:        %.2f ns/op%n", original.nsPerOp());
    OUT.printf("║   - Gas throughput: %.3f Mgas/s%n", original.mgasPerSecond());
    OUT.println("╠═══════════════════════════════════════════════════════════════════╣");
    OUT.println("║ Optimized Operation:");
    OUT.printf("║   - Operations:     %,d%n", optimized.operations);
    OUT.printf("║   - Total time:     %.2f ms%n", optimized.elapsedMs());
    OUT.printf("║   - Throughput:     %,.0f ops/sec%n", optimized.opsPerSecond());
    OUT.printf("║   - Latency:        %.2f ns/op%n", optimized.nsPerOp());
    OUT.printf("║   - Gas throughput: %.3f Mgas/s%n", optimized.mgasPerSecond());
    OUT.println("╠═══════════════════════════════════════════════════════════════════╣");
    OUT.printf("║ Speedup: %.2fx (%.1f%% faster)%n", speedup, percentFaster);
    OUT.println("╚═══════════════════════════════════════════════════════════════════╝");
    OUT.println();
  }

  // endregion

  // region Helper Classes

  private static class PerformanceResult {
    final long operations;
    final long elapsedNanos;
    final long gasPerOp;

    PerformanceResult(final long operations, final long elapsedNanos, final long gasPerOp) {
      this.operations = operations;
      this.elapsedNanos = elapsedNanos;
      this.gasPerOp = gasPerOp;
    }

    double elapsedMs() {
      return elapsedNanos / 1_000_000.0;
    }

    double nsPerOp() {
      return (double) elapsedNanos / operations;
    }

    double opsPerSecond() {
      return operations / (elapsedNanos / 1_000_000_000.0);
    }

    double mgasPerSecond() {
      long totalGas = operations * gasPerOp;
      double seconds = elapsedNanos / 1_000_000_000.0;
      return (totalGas / 1_000_000.0) / seconds;
    }
  }

  // endregion

  // region Helper Methods

  private MessageFrame createMessageFrame() {
    return MessageFrame.builder()
        .worldUpdater(mock(WorldUpdater.class))
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(mock(BlockValues.class))
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(Long.MAX_VALUE)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(Bytes32.ZERO)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(Code.EMPTY_CODE)
        .completer(__ -> {})
        .build();
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
