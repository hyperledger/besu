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

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public abstract class BinaryOperationBenchmark {

  protected static final int SAMPLE_SIZE = 30_000;

  protected Bytes[] aPool;
  protected Bytes[] bPool;
  protected int index;
  protected MessageFrame frame;

  @Setup()
  public void setUp() {
    frame = BenchmarkHelper.createMessageCallFrame();
    aPool = new Bytes[SAMPLE_SIZE];
    bPool = new Bytes[SAMPLE_SIZE];
    BenchmarkHelper.fillPool(aPool);
    BenchmarkHelper.fillPool(bPool);
    index = 0;
  }

  @Benchmark
  public void baseline() {
    frame.pushStackItem(bPool[index]);
    frame.pushStackItem(aPool[index]);
    frame.popStackItem();
    frame.popStackItem();

    index = (index + 1) % SAMPLE_SIZE;
  }

  @Benchmark
  public void executeOperation(final Blackhole blackhole) {
    frame.pushStackItem(bPool[index]);
    frame.pushStackItem(aPool[index]);

    blackhole.consume(invoke(frame));

    frame.popStackItem();

    index = (index + 1) % SAMPLE_SIZE;
  }

  protected abstract Operation.OperationResult invoke(MessageFrame frame);

  public <T> void fillPools(
      final Supplier<Integer> aSizeSupplier,
      final Supplier<Integer> bSizeSupplier,
      final Function<byte[], T> transform,
      final BiPredicate<T, T> swapOperands) {

    aPool = new Bytes[SAMPLE_SIZE];
    bPool = new Bytes[SAMPLE_SIZE];
    final Random random = new Random();

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      final int aSize = aSizeSupplier.get();
      final int bSize = bSizeSupplier.get();
      final byte[] a = new byte[aSize];
      final byte[] b = new byte[bSize];
      random.nextBytes(a);
      random.nextBytes(b);
      if (swapOperands.test(transform.apply(a), transform.apply(b))) {
        bPool[i] = Bytes.wrap(a);
        aPool[i] = Bytes.wrap(b);
      } else {
        aPool[i] = Bytes.wrap(a);
        bPool[i] = Bytes.wrap(b);
      }
    }
  }
}
