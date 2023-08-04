package org.hyperledger.besu.evmtool.benchmarks;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
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
import org.hyperledger.besu.evm.gascalculator.PetersburgGasCalculator;
import org.hyperledger.besu.evm.gascalculator.ShanghaiGasCalculator;
import org.hyperledger.besu.evm.gascalculator.SpuriousDragonGasCalculator;
import org.hyperledger.besu.evm.gascalculator.TangerineWhistleGasCalculator;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.io.PrintStream;
import java.util.ArrayDeque;
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
          .depth(1)
          .completer(__ -> {})
          .address(Address.ZERO)
          .blockHashLookup(n -> null)
          .blockValues(new SimpleBlockValues())
          .gasPrice(Wei.ZERO)
          .messageFrameStack(new ArrayDeque<>())
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
   * Run the benchmark with the speicific args. Execution will be done warmup + iterations times
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

    if (executions < 1) {
      return Double.NaN;
    }

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    return elapsed / executions;
  }

  /**
   * Return the gas calculator at a given fork. Some forks don't have a specific gas calculator and
   * will return the prior one
   *
   * @param fork name of the fork
   * @return a gas calculator
   */
  public static GasCalculator gasCalculatorForFork(final String fork) {
    return switch (fork) {
      case "Homestead" -> new HomesteadGasCalculator();
      case "Frontier" -> new FrontierGasCalculator();
      case "SpuriousDragon" -> new SpuriousDragonGasCalculator();
      case "TangerineWhistle" -> new TangerineWhistleGasCalculator();
      case "Byzantium" -> new ByzantiumGasCalculator();
      case "Constantinople" -> new ConstantinopleGasCalculator();
      case "Petersburg" -> new PetersburgGasCalculator();
      case "Istanbul" -> new IstanbulGasCalculator();
      case "Berlin" -> new BerlinGasCalculator();
      case "London", "Paris" -> new LondonGasCalculator();
      case "Shanghai" -> new ShanghaiGasCalculator();
      default -> new CancunGasCalculator();
    };
  }

  /**
   * Run the benchmarks
   *
   * @param output stream to print results to (typicall System.out)
   * @param attemptNative Should the benchmark attempt to us native libraries? (null use the
   *     default, false disabled, true enabled)
   * @param fork the fork name to run the benchmark against.
   */
  public abstract void runBenchmark(
      final PrintStream output, final Boolean attemptNative, final String fork);
}
