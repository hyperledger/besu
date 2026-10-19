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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EVMExecutor;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * A contract that makes {@value #CALLS} CALLs one after another, each to a contract that pushes
 * {@code calleeStackItems} items and returns 32 bytes. Every CALL builds a frame with its own
 * operand stack, so this measures what a frame's stack costs to set up and to fill.
 */
@State(Scope.Benchmark)
@Fork(value = 2)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class CallStackBenchmark {

  private static final int CALLS = 1000;
  private static final long GAS = 1_000_000_000L;
  private static final Address CALLER = Address.fromHexString("0xca11e4");
  private static final Address CALLEE = Address.fromHexString("0xca11ee");

  @Param({"v1", "v2"})
  public String evm;

  // the callee also pushes the two arguments of RETURN, so 1000 stays within the 1024 limit
  @Param({"0", "32", "64", "256", "1000"})
  public int calleeStackItems;

  private EVMExecutor executor;
  private SimpleWorld world;

  @Setup
  public void setUp() {
    final EvmConfiguration configuration =
        new EvmConfiguration(
            EvmConfiguration.DEFAULT.jumpDestCacheWeightKB(),
            EvmConfiguration.DEFAULT.worldUpdaterMode(),
            EvmConfiguration.DEFAULT.enableOptimizedOpcodes(),
            "v2".equals(evm));
    world = new SimpleWorld();
    world.createAccount(CALLER, 1, Wei.ZERO).setCode(callerCode());
    world.createAccount(CALLEE, 1, Wei.ZERO).setCode(calleeCode(calleeStackItems));

    final GasCounter gasCounter = new GasCounter();
    executor =
        new EVMExecutor(EvmSpec.evmSpec(EvmSpecVersion.CANCUN, BigInteger.ONE, configuration))
            .code(callerCode())
            .receiver(CALLER)
            .gas(GAS)
            .tracer(gasCounter);
    callInALoop();
    if (gasCounter.successfulCalls != CALLS) {
      throw new IllegalStateException(
          "Expected " + CALLS + " calls, got " + gasCounter.successfulCalls);
    }
    // the gas lets the results be read as Mgas/s: gas used / (µs per op)
    System.out.printf(
        "%n%s calleeStackItems=%d gas used=%d%n", evm, calleeStackItems, gasCounter.gasUsed);
    executor.tracer(OperationTracer.NO_TRACING);
  }

  @Benchmark
  public Bytes callInALoop() {
    return executor.worldUpdater(world.updater()).execute();
  }

  private static Bytes callerCode() {
    final ByteArrayOutputStream code = new ByteArrayOutputStream();
    // for i = CALLS; i != 0; i--: POP(CALL(GAS, CALLEE, 0, 0, 0, 0, 0))
    code.writeBytes(new byte[] {0x61, (byte) (CALLS >>> 8), (byte) CALLS}); // PUSH2 CALLS
    final int loop = code.size();
    code.write(0x5b); // JUMPDEST
    code.writeBytes(new byte[] {0x60, 0x01, (byte) 0x90, 0x03}); // PUSH1 1, SWAP1, SUB
    for (int i = 0; i < 5; i++) {
      code.write(0x5f); // PUSH0
    }
    code.write(0x73); // PUSH20
    code.writeBytes(CALLEE.getBytes().toArrayUnsafe());
    code.writeBytes(new byte[] {0x5a, (byte) 0xf1, 0x50}); // GAS, CALL, POP
    code.writeBytes(new byte[] {(byte) 0x80, 0x61, 0x00, (byte) loop, 0x57}); // DUP1, JUMPI
    code.write(0x00); // STOP
    return Bytes.wrap(code.toByteArray());
  }

  private static Bytes calleeCode(final int stackItems) {
    final ByteArrayOutputStream code = new ByteArrayOutputStream();
    for (int i = 0; i < stackItems; i++) {
      code.write(0x5f); // PUSH0
    }
    code.writeBytes(new byte[] {0x60, 0x20, 0x5f, (byte) 0xf3}); // RETURN(0, 32)
    return Bytes.wrap(code.toByteArray());
  }

  private static final class GasCounter implements OperationTracer {
    private int successfulCalls;
    private long gasUsed;

    @Override
    public void traceContextExit(final MessageFrame frame) {
      if (frame.getDepth() == 1 && frame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
        successfulCalls++;
      } else if (frame.getDepth() == 0) {
        gasUsed = GAS - frame.getRemainingGas();
      }
    }
  }
}
