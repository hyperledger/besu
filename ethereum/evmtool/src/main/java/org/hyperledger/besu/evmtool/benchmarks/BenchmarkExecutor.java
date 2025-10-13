/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evmtool.benchmarks;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.fluent.SimpleBlockValues;
import org.hyperledger.besu.evm.fluent.SimpleWorld;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.BerlinGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ByzantiumGasCalculator;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ConstantinopleGasCalculator;
import org.hyperledger.besu.evm.gascalculator.FrontierGasCalculator;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.HomesteadGasCalculator;
import org.hyperledger.besu.evm.gascalculator.IstanbulGasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.OsakaGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.io.PrintStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;

/** Abstract class to support benchmarking of various client algorithms */
public abstract class BenchmarkExecutor {

  static final int MATH_WARMUP = 10_000;
  static final int MATH_ITERATIONS = 1_000;

  int warmup;
  int iterations;

  static final MessageFrame fakeFrame =
      MessageFrame.builder()
          .type(MessageFrame.Type.CONTRACT_CREATION)
          .contract(Address.ZERO)
          .inputData(Bytes.EMPTY)
          .sender(Address.ZERO)
          .value(Wei.ZERO)
          .apparentValue(Wei.ZERO)
          .code(CodeV0.EMPTY_CODE)
          .completer(__ -> {})
          .address(Address.ZERO)
          .blockHashLookup((__, ___) -> null)
          .blockValues(new SimpleBlockValues())
          .gasPrice(Wei.ZERO)
          .miningBeneficiary(Address.ZERO)
          .originator(Address.ZERO)
          .initialGas(100_000L)
          .worldUpdater(new SimpleWorld())
          .build();

  /**
   * Run benchmarks with specified warmup and iterations
   *
   * @param warmup number of executions to run before timing
   * @param iterations number of executions to time.
   */
  protected BenchmarkExecutor(final int warmup, final int iterations) {
    this.warmup = warmup;
    this.iterations = iterations;
  }

  /** Run benchmarks with warmup and iterations set to MATH style benchmarks. */
  protected BenchmarkExecutor() {
    this(MATH_WARMUP, MATH_ITERATIONS);
  }

  /**
   * Run the benchmark with the specific args. Execution will be done warmup + iterations times
   *
   * @param arg the bytes areguments to pass into the contract
   * @param contract the precompiled contract to benchmark
   * @return the mean number of seconds each timed iteration took.
   */
  protected double runPrecompileBenchmark(final Bytes arg, final PrecompiledContract contract) {
    if (contract.computePrecompile(arg, fakeFrame).getOutput() == null) {
      throw new RuntimeException("Input is Invalid");
    }

    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < warmup && timer.elapsed().getSeconds() < 1; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }
    timer.reset();
    timer.start();
    int executions = 0;
    while (executions < iterations && timer.elapsed().getSeconds() < 1) {
      contract.computePrecompile(arg, fakeFrame);
      executions++;
    }
    timer.stop();

    if (executions > 0) {
      final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
      return elapsed / executions;
    } else {
      return Double.NaN;
    }
  }

  /**
   * Return the gas calculator at a given fork. Some forks don't have a specific gas calculator and
   * will return the prior one
   *
   * @param fork name of the fork
   * @return a gas calculator
   */
  public static GasCalculator gasCalculatorForFork(final String fork) {
    return switch (EvmSpecVersion.valueOf(fork.toUpperCase(Locale.ROOT))) {
      case HOMESTEAD -> new HomesteadGasCalculator();
      case FRONTIER -> new FrontierGasCalculator();
      case TANGERINE_WHISTLE -> null;
      case SPURIOUS_DRAGON -> null;
      case BYZANTIUM -> new ByzantiumGasCalculator();
      case CONSTANTINOPLE -> new ConstantinopleGasCalculator();
      case PETERSBURG -> new PetersburgGasCalculator();
      case ISTANBUL -> new IstanbulGasCalculator();
      case BERLIN -> new BerlinGasCalculator();
      case LONDON, PARIS -> new LondonGasCalculator();
      case SHANGHAI -> new ShanghaiGasCalculator();
      case CANCUN -> new CancunGasCalculator();
      case CANCUN_EOF -> new OsakaGasCalculator();
      case PRAGUE -> new PragueGasCalculator();
      case OSAKA -> new OsakaGasCalculator();
      case AMSTERDAM, BOGOTA, POLIS, BANGKOK, FUTURE_EIPS, EXPERIMENTAL_EIPS ->
          new OsakaGasCalculator();
    };
  }

  /**
   * Run the benchmarks
   *
   * @param output stream to print results to (typically System.out)
   * @param attemptNative Should the benchmark attempt to us native libraries? (null use the
   *     default, false disabled, true enabled)
   * @param fork the fork name to run the benchmark against.
   */
  public abstract void runBenchmark(
      final PrintStream output, final Boolean attemptNative, final String fork);
}
