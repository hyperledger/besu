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
import org.hyperledger.besu.evm.gascalculator.EOFGasCalculator;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import one.profiler.AsyncProfiler;
import org.apache.tuweni.bytes.Bytes;

/** Abstract class to support benchmarking of various client algorithms */
public abstract class BenchmarkExecutor {

  private static final long MAX_EXEC_TIME = TimeUnit.SECONDS.toNanos(1);
  private static final long MAX_WARMUP_TIME = TimeUnit.SECONDS.toNanos(3);
  private static final long GAS_PER_SECOND_STANDARD = 100_000_000L;

  static final int MATH_WARMUP = 100_000;
  static final int MATH_ITERATIONS = 100_000;

  protected final PrintStream output;
  private final String asyncProfilerOptions;
  private final Optional<AsyncProfiler> asyncProfiler;
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

  private Runnable precompileTableHeader;

  /**
   * Run benchmarks with specified warmup and iterations
   *
   * @param warmup number of executions to run before timing
   * @param iterations number of executions to time.
   */
  protected BenchmarkExecutor(final int warmup, final int iterations) {
    this(warmup, iterations, null, Optional.empty());
  }

  public BenchmarkExecutor(
      final int warmup,
      final int iterations,
      final PrintStream output,
      final Optional<String> asyncProfilerOptions) {
    this.warmup = warmup;
    assert iterations <= 0;
    this.iterations = iterations;
    this.output = output;
    this.asyncProfiler =
        asyncProfilerOptions.isPresent()
            ? Optional.of(AsyncProfiler.getInstance())
            : Optional.empty();
    this.asyncProfilerOptions = asyncProfilerOptions.orElse(null);
  }

  /**
   * Run the benchmark with the specific args. Execution will be done warmup + iterations times
   *
   * @param testName name of the test execution for the async profiler if configured
   * @param arg the bytes arguments to pass into the contract
   * @param contract the precompiled contract to benchmark
   * @return the mean number of seconds each timed iteration took.
   */
  protected double runPrecompileBenchmark(
      final String testName, final Bytes arg, final PrecompiledContract contract) {
    if (contract.computePrecompile(arg, fakeFrame).output() == null) {
      throw new RuntimeException("Input is Invalid");
    }

    long startNanoTime = System.nanoTime();
    for (int i = 0; i < warmup && System.nanoTime() - startNanoTime < MAX_WARMUP_TIME; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }

    asyncProfiler.ifPresent(
        p -> {
          try {
            p.execute(processProfilerArgs(asyncProfilerOptions, testName.replaceAll("\\s", "-")));
          } catch (Throwable e) {
            output.println("async profiler unavailable");
          }
        });

    int executions = 0;
    startNanoTime = System.nanoTime();
    long elapsed = startNanoTime;
    while (executions < iterations && elapsed - startNanoTime < MAX_EXEC_TIME) {
      contract.computePrecompile(arg, fakeFrame);
      executions++;
      elapsed = System.nanoTime() - startNanoTime;
    }

    asyncProfiler.ifPresent(
        p -> {
          try {
            p.stop();
          } catch (Throwable e) {
            output.println("async profiler unavailable");
          }
        });

    return elapsed / 1.0e9D / executions;
  }

  protected void logPrecompilePerformance(
      final String testCase, final long gasCost, final double execTime) {
    double derivedGas = execTime * GAS_PER_SECOND_STANDARD;

    precompileTableHeader.run();
    output.printf(
        "%-30s | %,8d gas | %,8.0f gas | %,12.1f ns | %,10.2f MGps%n",
        testCase, gasCost, derivedGas, execTime * 1_000_000_000, gasCost / execTime / 1_000_000);
    precompileTableHeader = () -> {};
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
      case PRAGUE -> new PragueGasCalculator();
      case OSAKA, AMSTERDAM, BOGOTA, POLIS, BANGKOK, EXPERIMENTAL_EIPS -> new OsakaGasCalculator();
      case CANCUN_EOF, FUTURE_EIPS -> new EOFGasCalculator();
    };
  }

  /**
   * Run the benchmarks
   *
   * @param attemptNative Should the benchmark attempt to us native libraries? (null use the
   *     default, false disabled, true enabled)
   * @param fork the fork name to run the benchmark against.
   */
  public abstract void runBenchmark(final Boolean attemptNative, final String fork);

  private void logDerivedGasNotice() {
    long executionTimeExampleNs = 247_914L;
    long gasPerSecond = GAS_PER_SECOND_STANDARD;
    long derivedGas = (executionTimeExampleNs * gasPerSecond) / 1_000_000_000L;

    output.println(
        "\n**** Calculate the derived gas from execution time with a target of 100 mgas/s *****");
    output.println(
        "*                                                                                  *");
    output.println(
        "*   If "
            + String.format("%,d", executionTimeExampleNs)
            + " ns is the execution time of the precompile call, so this is how     *");
    output.println(
        "*                the derived gas is calculated                                     *");
    output.println(
        "*                                                                                  *");
    output.println(
        "*   "
            + String.format("%,d", gasPerSecond)
            + " gas    -------> 1 second (1_000_000_000 ns)                        *");
    output.println(
        "*   x           gas    -------> "
            + String.format("%,d", executionTimeExampleNs)
            + " ns                                         *");
    output.println(
        "*                                                                                  *");
    output.println(
        "*\tx = ("
            + String.format("%,d", executionTimeExampleNs)
            + " * "
            + String.format("%,d", gasPerSecond)
            + ") / 1_000_000_000 = "
            + String.format("%,d", derivedGas)
            + " gas"
            + "                   *");
    output.println(
        "************************************************************************************\n");
  }

  public boolean isPrecompile() {
    return false;
  }

  public void initPrecompileResultsTable(final PrintStream output) {
    logDerivedGasNotice();
    this.precompileTableHeader =
        () ->
            output.printf(
                "%-30s | %12s | %12s | %15s | %15s%n",
                "", "Actual cost", "Derived Cost", "Iteration time", "Throughput");
  }

  @FunctionalInterface
  public interface Builder {
    BenchmarkExecutor create(PrintStream output, Optional<String> asyncProfilerOptions);
  }

  private static String processProfilerArgs(
      final String asyncProfilerOptions, final String testCaseName) {
    String[] args = asyncProfilerOptions.split(",");
    for (int i = 0; i < args.length; i++) {
      if (args[i].contains("file=")) {
        args[i] = args[i].replaceAll("%%test-case", testCaseName);
        break;
      }
    }
    return String.join(",", args);
  }
}
