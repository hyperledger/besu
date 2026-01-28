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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SarOperationOptimized;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.operation.SarOperationOptimized2;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

public class SarOperationOptimizedBenchmark extends BinaryOperationBenchmark {

  // Define available scenarios to test different execution paths
  public enum Case {
    // Shift by 0 - early return path
    SHIFT_0,
    // Negative number (ALL_BITS) with shift=1 - tests sign extension OR path
    NEGATIVE_SHIFT_1,
    // Positive number with shift=1 - no sign extension needed
    POSITIVE_SHIFT_1,
    // Negative number with medium shift
    NEGATIVE_SHIFT_128,
    // Positive number with medium shift
    POSITIVE_SHIFT_128,
    // Overflow: shift >= 256
    OVERFLOW_SHIFT_256,
    // Overflow: shift amount > 4 bytes
    OVERFLOW_LARGE_SHIFT,
    // Random values (original behavior)
    FULL_RANDOM
  }

  private static final Bytes ALL_BITS =
      Bytes.fromHexString("0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
  private static final Bytes POSITIVE_VALUE =
      Bytes.fromHexString("0x7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");

  @Param({
    "SHIFT_0",
    "NEGATIVE_SHIFT_1",
    "POSITIVE_SHIFT_1",
    "NEGATIVE_SHIFT_128",
    "POSITIVE_SHIFT_128",
    "OVERFLOW_SHIFT_256",
    "OVERFLOW_LARGE_SHIFT",
    "FULL_RANDOM"
  })
  private String caseName;

  @Setup(Level.Iteration)
  @Override
  public void setUp() {
    frame = BenchmarkHelper.createMessageCallFrame();

    final Case scenario = Case.valueOf(caseName);
    aPool = new Bytes[SAMPLE_SIZE]; // shift amount (pushed second, popped first)
    bPool = new Bytes[SAMPLE_SIZE]; // value (pushed first, popped second)

    final ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      switch (scenario) {
        case SHIFT_0:
          aPool[i] = Bytes.of(0);
          bPool[i] = ALL_BITS;
          break;

        case NEGATIVE_SHIFT_1:
          // shiftAmount = 0x1, value = 0xfff...fff (negative, tests OR path)
          aPool[i] = Bytes.of(1);
          bPool[i] = ALL_BITS;
          break;

        case POSITIVE_SHIFT_1:
          // shiftAmount = 0x1, value = 0x7ff...fff (positive, no sign extension)
          aPool[i] = Bytes.of(1);
          bPool[i] = POSITIVE_VALUE;
          break;

        case NEGATIVE_SHIFT_128:
          aPool[i] = Bytes.of(128);
          bPool[i] = ALL_BITS;
          break;

        case POSITIVE_SHIFT_128:
          aPool[i] = Bytes.of(128);
          bPool[i] = POSITIVE_VALUE;
          break;

        case OVERFLOW_SHIFT_256:
          // Shift of exactly 256 - overflow path
          aPool[i] = Bytes.fromHexString("0x0100"); // 256
          bPool[i] = ALL_BITS;
          break;

        case OVERFLOW_LARGE_SHIFT:
          // Shift amount > 4 bytes - overflow path
          aPool[i] = Bytes.fromHexString("0x010000000000"); // > 4 bytes
          bPool[i] = ALL_BITS;
          break;

        case FULL_RANDOM:
        default:
          // Original random behavior
          final byte[] shift = new byte[1 + random.nextInt(4)];
          final byte[] value = new byte[1 + random.nextInt(32)];
          random.nextBytes(shift);
          random.nextBytes(value);
          aPool[i] = Bytes.wrap(shift);
          bPool[i] = Bytes.wrap(value);
          break;
      }
    }
    index = 0;
  }

  @Override
  protected Operation.OperationResult invoke(final MessageFrame frame) {
    return SarOperationOptimized.staticOperation(frame);
  }
}
