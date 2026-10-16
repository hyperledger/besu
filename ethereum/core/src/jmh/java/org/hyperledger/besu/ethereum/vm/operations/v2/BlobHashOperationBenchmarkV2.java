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
package org.hyperledger.besu.ethereum.vm.operations.v2;

import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.v2.operation.BlobHashOperationV2;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
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
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
public class BlobHashOperationBenchmarkV2 {

  static final List<VersionedHash> VERSIONED_HASHES =
      List.of(
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef00"),
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef01"),
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef02"),
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef03"),
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef04"),
          VersionedHash.fromHexString(
              "0x01cafebabeb0b0facedeadbeefbeef0001cafebabeb0b0facedeadbeefbeef05"));

  public enum Scenario {
    /** Random index of an existing versioned hash. */
    VALID,
    /** Random index that fits in an int but is past the last versioned hash. */
    OUT_OF_BOUNDS,
    /** Random index with a non-zero upper limb. */
    TOO_LARGE,
    /** Random in-range index, but the transaction has no versioned hashes. */
    NO_BLOBS
  }

  @Param public Scenario scenario;

  private BlobHashOperationV2 operation;
  private MessageFrame frame;
  private UInt256[] blobIndexPool;
  private int poolIndex;

  @Setup
  public void setup() {
    operation = new BlobHashOperationV2(new CancunGasCalculator());
    final Optional<List<VersionedHash>> versionedHashes =
        scenario == Scenario.NO_BLOBS ? Optional.empty() : Optional.of(VERSIONED_HASHES);
    frame = BenchmarkHelperV2.messageCallFrameBuilder().versionedHashes(versionedHashes).build();
    blobIndexPool = blobIndexPool(scenario);
    poolIndex = 0;
  }

  @Benchmark
  public void executeOperation(final Blackhole blackhole) {
    BenchmarkHelperV2.pushUInt256(frame, blobIndexPool[poolIndex]);
    blackhole.consume(operation.execute(frame, null));
    frame.setTopV2(0);
    poolIndex = (poolIndex + 1) % BenchmarkHelperV2.SAMPLE_SIZE;
  }

  /**
   * Creates the seeded pool of blob indexes for a scenario. Shared with the V1 benchmark so both
   * see the same inputs.
   *
   * @param scenario the scenario
   * @return the pool of blob indexes
   */
  static UInt256[] blobIndexPool(final Scenario scenario) {
    final Random random = new Random(BenchmarkHelperV2.SEED);
    final int size = VERSIONED_HASHES.size();
    final UInt256[] pool = new UInt256[BenchmarkHelperV2.SAMPLE_SIZE];
    for (int i = 0; i < pool.length; i++) {
      pool[i] =
          switch (scenario) {
            case VALID, NO_BLOBS -> UInt256.fromLong(random.nextInt(size));
            case OUT_OF_BOUNDS -> UInt256.fromLong(random.nextInt(size, Integer.MAX_VALUE));
            case TOO_LARGE ->
                new UInt256(
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong() | 1L,
                    random.nextLong());
          };
    }
    return pool;
  }
}
