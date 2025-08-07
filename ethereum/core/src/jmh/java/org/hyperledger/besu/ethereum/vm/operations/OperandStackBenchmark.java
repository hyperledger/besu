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
import org.hyperledger.besu.evm.internal.OperandStack;

import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@Warmup(iterations = 6, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class OperandStackBenchmark {
  private static final int OPERATIONS_PER_INVOCATION = 1000;

  @Param({"6", "15", "34", "100", "234", "500", "800", "1024"})
  private int stackDepth;

  private static final Bytes BYTES =
      Bytes.fromHexString("0x3232323232323232323232323232323232323232323232323232323232323232");

  @Benchmark
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void fillUp() {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      OperandStack stack = new OperandStack(MessageFrame.DEFAULT_MAX_STACK_SIZE);
      for (int j = 0; j < stackDepth; j++) {
        stack.push(BYTES);
      }
    }
  }
}
