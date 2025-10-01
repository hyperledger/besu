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
package org.hyperledger.besu.evm;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;

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
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public class UInt256FromBytesBEBenchmark {
  protected static final int SAMPLE_SIZE = 30_000;
  protected Bytes[] pool;
  protected int index;

  // Define available scenarios
  public enum Case {
    FromBytes_32(1),
    FromBytes_64(2),
    FromBytes_128(4),
    FromBytes_196(6),
    FromBytes_256(8),
    FULL_RANDOM(-1);

    final int size;

    Case(final int size) {
      this.size = size;
    }
  }

  @Param({
    "FromBytes_32",
    "FromBytes_64",
    "FromBytes_128",
    "FromBytes_196",
    "FromBytes_256",
    "FULL_RANDOM"
  })
  private String caseName;

  @Setup(Level.Iteration)
  public void setUp() {
    Case scenario = Case.valueOf(caseName);
    pool = new Bytes[SAMPLE_SIZE];

    final ThreadLocalRandom random = ThreadLocalRandom.current();
    int size;

    for (int i = 0; i < SAMPLE_SIZE; i++) {
      if (scenario.size < 0) size = random.nextInt(1, 33);
      else size = scenario.size * 4;
      final byte[] a = new byte[size];
      random.nextBytes(a);
      pool[i] = Bytes.wrap(a);
    }
    index = 0;
  }

  @Benchmark
  public void executeOperation(final Blackhole blackhole) {
    blackhole.consume(invoke(pool[index]));
    index = (index + 1) % SAMPLE_SIZE;
  }

  private UInt256 invoke(final Bytes el) {
    return UInt256.fromBytesBE(el.toArrayUnsafe());
  }
}
