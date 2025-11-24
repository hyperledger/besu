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

import org.hyperledger.besu.util.HexUtils;

import java.nio.ByteBuffer;
import java.util.Random;
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
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(value = TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class ToFastHexBenchmark {

  // engine_getBlobsV2 worse case sizes:
  // 1 blob = 128 KB = 131072 bytes + (1 * 128 proofs * 48 bytes) = 137,216 bytes
  // 9 blobs = 9 * 131072 = 1,179,648 bytes + (9 * 128 proofs * 48 bytes) = 1,234,944 bytes (~1.2
  // MB)
  // 21 blobs = 21 * 131072 = 2,752,512 bytes + (21 * 128 proofs * 48 bytes) = 2,881,536 bytes (~2.7
  // MB)
  // 72 blobs = 72 * 131072 = 9,437,184 bytes + (72 * 128 proofs * 48 bytes) = 9,879,552 bytes (~9.4
  // MB)
  // 128 blobs = 128 * 131072 = 16,777,216 bytes + (128 * 128 proofs * 48 bytes) = 17,563,648 bytes
  // (~16.7 MB)
  public enum Case {
    SIZE_1_BLOB(137_216),
    SIZE_9_BLOBS(1_234_944),
    SIZE_21_BLOBS(2_881_536),
    SIZE_72_BLOBS(9_879_552),
    SIZE_128_BLOBS(17_563_648);

    final int inputSize;

    Case(final int inputSize) {
      this.inputSize = inputSize;
    }
  }

  @Param({"SIZE_1_BLOB", "SIZE_9_BLOBS", "SIZE_21_BLOBS", "SIZE_72_BLOBS", "SIZE_128_BLOBS"})
  private Case caseName;

  private static final int N = 3;
  private static final int FACTOR = 1;
  private static final Random RANDOM = new Random(23L);
  Bytes[] bytes;

  @Setup
  public void setup() {
    // force megamorphic tuweni behavior by mixing different Bytes implementations
    bytes = new Bytes[N * FACTOR];
    for (int i = 0; i < N * FACTOR; i += N) {
      bytes[i] = Bytes.wrap(getBytes(caseName.inputSize));
      bytes[i + 1] = Bytes.wrapByteBuffer(ByteBuffer.wrap(getBytes(caseName.inputSize)));
      bytes[i + 2] = Bytes.repeat((byte) 0x09, caseName.inputSize);
    }
  }

  private static byte[] getBytes(final int size) {
    byte[] b = new byte[size];
    RANDOM.nextBytes(b);
    return b;
  }

  @Benchmark
  @OperationsPerInvocation(N * FACTOR)
  public void hexUtils(final Blackhole blackhole) {
    for (Bytes b : bytes) {
      blackhole.consume(HexUtils.toFastHex(b, true));
    }
  }

  @Benchmark
  @OperationsPerInvocation(N * FACTOR)
  public void tuweni(final Blackhole blackhole) {
    for (Bytes b : bytes) {
      blackhole.consume(b.toFastHex(true));
    }
  }
}
