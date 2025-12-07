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
import org.hyperledger.besu.evm.UInt256;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;


@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class UIntFromBytesBenchmark {
  // Benches for UInt256.fromBytesBE

  protected static final int SAMPLE_SIZE = 30_000;
  protected byte[][] pool;
  protected int index;

  // Define available scenarios
  public enum Case {
    SIZE_0_PAD_0(0, 0),
    SIZE_0_PAD_64(0, 8),
    SIZE_0_PAD_128(0, 16),
    SIZE_0_PAD_256(0, 32),
    SIZE_64_PAD_0(8, 0),
    SIZE_64_PAD_16(8, 2),
    SIZE_64_PAD_192(8, 24),
    SIZE_128_PAD_0(16, 0),
    SIZE_128_PAD_32(16, 4),
    SIZE_128_PAD_128(16, 16),
    SIZE_256_PAD_0(32, 0),
    SIZE_RAND_PAD_0(-1, 0),
    SIZE_RAND_PAD_RAND(-1, -1);

    final int size;
    final int padSize;

    Case(final int size, final int padSize) {
      this.size = size;
      this.padSize = padSize;
    }
  }

  @Param({
    "SIZE_0_PAD_0",
    "SIZE_0_PAD_64",
    "SIZE_0_PAD_128",
    "SIZE_0_PAD_256",
    "SIZE_64_PAD_0",
    "SIZE_64_PAD_16",
    "SIZE_64_PAD_192",
    "SIZE_128_PAD_0",
    "SIZE_128_PAD_32",
    "SIZE_128_PAD_128",
    "SIZE_256_PAD_0",
    "SIZE_RAND_PAD_0",
    "SIZE_RAND_PAD_RAND"
  })

  private String caseName;

  @Setup(Level.Iteration)
  public void setUp() {
    Case scenario = Case.valueOf(caseName);
    pool = new byte[SAMPLE_SIZE][];

    final ThreadLocalRandom random = ThreadLocalRandom.current();
    int size = scenario.size;
    int padSize = scenario.padSize;
    if (scenario.size > 0) size = Math.max(0, size - random.nextInt(0, 4));
    if (scenario.padSize > 0) padSize = Math.max(0, padSize - random.nextInt(0, 4));

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      if (scenario.size < 0) size = random.nextInt(1, 33);
      if (scenario.padSize < 0) padSize = random.nextInt(0, 33 - size);
      final byte[] a = new byte[size];
      final byte[] b = new byte[size + padSize];
      random.nextBytes(a);
      System.arraycopy(a, 0, b, padSize, size);
      pool[i] = b;
    }
    index = 0;
  }

  @Benchmark
  public void fromBytesBE(final Blackhole blackhole) {
    blackhole.consume(UInt256.fromBytesBE(pool[index]));
    index = (index + 1) % SAMPLE_SIZE;
  }
}

