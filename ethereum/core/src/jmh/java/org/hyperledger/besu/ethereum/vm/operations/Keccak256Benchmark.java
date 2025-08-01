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

import org.hyperledger.besu.crypto.Hash;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Thread)
@Warmup(iterations = 8, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@Fork(2)
@BenchmarkMode(Mode.AverageTime)
public class Keccak256Benchmark {
  private static final int OPERATIONS_PER_INVOCATION = 20_000;

  @Param({"32", "64", "128", "256", "512"})
  private String inputSize;

  public Bytes bytes;

  @Setup
  public void setUp() {
    final Random random = new Random();
    final byte[] byteArray = new byte[Integer.parseInt(inputSize)];
    random.nextBytes(byteArray);
    bytes = Bytes.wrap(byteArray);
  }

  @Benchmark
  @OperationsPerInvocation(OPERATIONS_PER_INVOCATION)
  public void executeOperation() {
    for (int i = 0; i < OPERATIONS_PER_INVOCATION; i++) {
      Hash.keccak256(bytes);
    }
  }
}
