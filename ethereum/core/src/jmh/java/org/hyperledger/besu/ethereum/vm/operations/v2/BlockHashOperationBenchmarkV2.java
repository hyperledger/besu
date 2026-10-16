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

import static org.hyperledger.besu.ethereum.vm.operations.v2.BlockHashBenchmarkChain.CURRENT_BLOCK;
import static org.hyperledger.besu.ethereum.vm.operations.v2.BlockHashBenchmarkChain.LOOKBACK;

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.v2.operation.BlockHashOperationV2;

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
public class BlockHashOperationBenchmarkV2 {

  public enum Scenario {
    /** Random block within the lookback window. */
    VALID,
    /** Random block at or after the current block. */
    CURRENT_OR_FUTURE,
    /** Random block older than the lookback window. */
    OUTSIDE_LOOKBACK,
    /** Random block number with a non-zero upper limb. */
    TOO_LARGE
  }

  @Param public Scenario scenario;

  private BlockHashOperationV2 operation;
  private MessageFrame frame;
  private UInt256[] requestedBlockPool;
  private int poolIndex;

  @Setup
  public void setup() {
    operation = new BlockHashOperationV2(new PetersburgGasCalculator());
    final BlockHashBenchmarkChain chain = BlockHashBenchmarkChain.create();
    frame =
        BenchmarkHelperV2.messageCallFrameBuilder()
            .blockValues(chain.currentHeader())
            .blockHashLookup(chain.blockHashLookup())
            .build();
    requestedBlockPool = requestedBlockPool(scenario);
    poolIndex = 0;
  }

  @Benchmark
  public void executeOperation(final Blackhole blackhole) {
    BenchmarkHelperV2.pushUInt256(frame, requestedBlockPool[poolIndex]);
    blackhole.consume(operation.execute(frame, null));
    frame.setTopV2(0);
    poolIndex = (poolIndex + 1) % BenchmarkHelperV2.SAMPLE_SIZE;
  }

  /**
   * Creates the seeded pool of requested block numbers for a scenario. Shared with the V1 benchmark
   * so both see the same inputs.
   *
   * @param scenario the scenario
   * @return the pool of requested block numbers
   */
  static UInt256[] requestedBlockPool(final Scenario scenario) {
    final Random random = new Random(BenchmarkHelperV2.SEED);
    final UInt256[] pool = new UInt256[BenchmarkHelperV2.SAMPLE_SIZE];
    for (int i = 0; i < pool.length; i++) {
      pool[i] =
          switch (scenario) {
            case VALID ->
                UInt256.fromLong(random.nextLong(CURRENT_BLOCK - LOOKBACK, CURRENT_BLOCK));
            case CURRENT_OR_FUTURE ->
                UInt256.fromLong(random.nextLong(CURRENT_BLOCK, CURRENT_BLOCK + LOOKBACK));
            case OUTSIDE_LOOKBACK -> UInt256.fromLong(random.nextLong(CURRENT_BLOCK - LOOKBACK));
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
