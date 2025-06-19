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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import one.profiler.AsyncProfiler;
import org.apache.tuweni.bytes.Bytes;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

/** Abstract class to support benchmarking of various client algorithms */
public abstract class BenchmarkExecutor {

  private static final int MAX_EXEC_TIME_IN_SECONDS = 1;
  private static final int MAX_WARMUP_TIME_IN_SECONDS = 3;
  private static final long GAS_PER_SECOND_STANDARD = 100_000_000L;

  static final int MATH_WARMUP = 100_000;
  static final int MATH_ITERATIONS = 100_000;

  /** Where to write the output of the benchmarks. */
  protected final PrintStream output;

  private final BenchmarkConfig config;

  private Runnable precompileTableHeader;
  int warmIterations;
  private final long warmTimeInNano;
  int execIterations;
  private final long execTimeInNano;

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
   * @param output print stream to print the output to.
   * @param benchmarkConfig options to give to the benchmark runner.
   */
  public BenchmarkExecutor(
      final int warmup,
      final int iterations,
      final PrintStream output,
      final BenchmarkConfig benchmarkConfig) {
    this.warmIterations =
        benchmarkConfig
            .warmIterations()
            .orElseGet(() -> benchmarkConfig.warmTime().isEmpty() ? warmup : Integer.MAX_VALUE);
    this.warmTimeInNano =
        TimeUnit.SECONDS.toNanos(
            benchmarkConfig
                .warmTime()
                .orElseGet(
                    () ->
                        benchmarkConfig.warmIterations().isEmpty()
                            ? MAX_WARMUP_TIME_IN_SECONDS
                            : Integer.MAX_VALUE));
    this.execIterations =
        benchmarkConfig
            .execIterations()
            .orElseGet(() -> benchmarkConfig.execTime().isEmpty() ? iterations : Integer.MAX_VALUE);
    this.execTimeInNano =
        TimeUnit.SECONDS.toNanos(
            benchmarkConfig
                .execTime()
                .orElseGet(
                    () ->
                        benchmarkConfig.warmIterations().isEmpty()
                            ? MAX_EXEC_TIME_IN_SECONDS
                            : Integer.MAX_VALUE));
    this.output = output;
    this.precompileTableHeader =
        () ->
            output.printf(
                "%-30s | %12s | %12s | %15s | %15s%n",
                "", "Actual cost", "Derived Cost", "Iteration time", "Throughput");
    this.config = benchmarkConfig;
    assert warmIterations <= 0;
    assert execIterations <= 0;
  }

  public void precompile(final Map<String, Bytes> testCases, final PrecompiledContract contract) {
    for (final Map.Entry<String, Bytes> testCase : testCases.entrySet()) {
      if (config.testCasePattern().isPresent()
        && !Pattern.compile(config.testCasePattern().get()).matcher(testCase.getKey()).find()) {
        continue;
      }

      final double execTime =
        runPrecompileBenchmark(testCase.getKey(), testCase.getValue(), contract);

      long gasCost = contract.gasRequirement(testCase.getValue());
      logPrecompilePerformance(testCase.getKey(), gasCost, execTime);
    }
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
    for (int i = 0; i < warmIterations && System.nanoTime() - startNanoTime < warmTimeInNano; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }

    final AtomicReference<AsyncProfiler> asyncProfiler = new AtomicReference<>();
    config
        .asyncProfilerOptions()
        .ifPresent(
            options -> {
              asyncProfiler.set(AsyncProfiler.getInstance());
              try {
                asyncProfiler
                    .get()
                    .execute(processProfilerArgs(options, testName.replaceAll("\\s", "-")));
              } catch (Throwable t) {
                output.println("async profiler unavailable: " + t.getMessage());
              }
            });

    int executions = 0;
    long elapsed = 0;
    startNanoTime = System.nanoTime();
    while (executions < execIterations && elapsed < execTimeInNano) {
      contract.computePrecompile(arg, fakeFrame);
      executions++;
      elapsed = System.nanoTime() - startNanoTime;
    }

    if (asyncProfiler.get() != null) {
      try {
        asyncProfiler.get().stop();
      } catch (Throwable t) {
        output.println("async profiler unavailable: " + t.getMessage());
      }
    }

    return elapsed / 1.0e9D / executions;
  }

  private void logPrecompilePerformance(
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

  /**
   * Little disclaimer about how derived gas is computed for Precompiles.
   *
   * @param output print stream to print the output to.
   */
  public static void logPrecompileDerivedGasNotice(final PrintStream output) {
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

  /**
   * Check if this is a Precompile.
   *
   * @return true if this is a benchmark concerning a Precompile, false otherwise
   */
  public boolean isPrecompile() {
    return false;
  }

  /** Interface in how to construct a BenchmarkExecutor statically. */
  @FunctionalInterface
  public interface Builder {
    /**
     * Creates a new BenchmarkExecutor.
     *
     * @param output where to write the stats.
     * @param asyncProfilerOptions starting options for the AsyncProfiler.
     * @return the newly created executor.
     */
    BenchmarkExecutor create(PrintStream output, BenchmarkConfig asyncProfilerOptions);
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
