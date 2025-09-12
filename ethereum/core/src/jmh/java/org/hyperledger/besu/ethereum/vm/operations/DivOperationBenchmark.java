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
import org.hyperledger.besu.evm.operation.DivOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

public class DivOperationBenchmark extends BinaryOperationBenchmark {

  private static final Random RANDOM = new Random();

  // public because of jmh code generator
  public enum MODE {
    ZERO_QUOTIENT,
    ONE_QUOTIENT,
    LARGER_QUOTIENT,
    UNIFORM_OPERATORS,
    INT_OPERATORS,
    LONG_OPERATORS,
    SMALL_QUOTIENT,
    BIG_OPERATORS,
    FULL_RANDOM
  }

  @Param private MODE mode;

  @Setup
  @Override
  public void setUp() {
    frame = BenchmarkHelper.createMessageCallFrame();

    switch (mode) {
      case ZERO_QUOTIENT:
        aPool = new Bytes[SAMPLE_SIZE];
        bPool = new Bytes[SAMPLE_SIZE];
        Arrays.fill(aPool, Bytes.EMPTY);
        BenchmarkHelper.fillPool(bPool, () -> 1 + RANDOM.nextInt(32));
        break;
      case ONE_QUOTIENT:
        aPool = new Bytes[SAMPLE_SIZE];
        BenchmarkHelper.fillPool(aPool, () -> 1 + RANDOM.nextInt(32));
        bPool = aPool;
        break;
      case LARGER_QUOTIENT:
        fillPools(
            () -> 22 + RANDOM.nextInt(10),
            () -> 1 + RANDOM.nextInt(10),
            byteArray -> new BigInteger(1, byteArray),
            (__, ___) -> true);
        break;
      case SMALL_QUOTIENT:
        fillPools(
            () -> 24 + RANDOM.nextInt(9),
            () -> 24 + RANDOM.nextInt(9),
            byteArray -> new BigInteger(1, byteArray),
            (a, b) -> a.compareTo(b) > 0);
        break;
      case UNIFORM_OPERATORS:
        fillPools(
            () -> 1 + RANDOM.nextInt(32),
            () -> 1 + RANDOM.nextInt(32),
            byteArray -> new BigInteger(1, byteArray),
            (a, b) -> a.compareTo(b) > 0);
        break;
      case INT_OPERATORS:
        fillPools(
            () -> 5 + RANDOM.nextInt(4),
            () -> 1 + RANDOM.nextInt(4),
            byteArray -> new BigInteger(1, byteArray),
            (__, ___) -> true);
        break;
      case LONG_OPERATORS:
        fillPools(
            () -> 9 + RANDOM.nextInt(5),
            () -> 4 + RANDOM.nextInt(5),
            byteArray -> new BigInteger(1, byteArray),
            (__, ___) -> true);
        break;
      case BIG_OPERATORS:
        fillPools(
            () -> 17 + RANDOM.nextInt(16),
            () -> 17 + RANDOM.nextInt(16),
            byteArray -> new BigInteger(1, byteArray),
            (a, b) -> a.compareTo(b) > 0);
        break;
      case FULL_RANDOM:
        fillPools(
            () -> 1 + RANDOM.nextInt(32), () -> 1 + RANDOM.nextInt(32), __ -> 0, (__, ___) -> true);
        break;
    }
    index = 0;
  }

  @Override
  protected Operation.OperationResult invoke(final MessageFrame frame) {
    return DivOperation.staticOperation(frame);
  }
}
