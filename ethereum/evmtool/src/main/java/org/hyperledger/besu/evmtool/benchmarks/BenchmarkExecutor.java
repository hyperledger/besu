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

  protected BenchmarkExecutor(final int warmup, final int iterations) {
    this.warmup = warmup;
    this.iterations = iterations;
  }

  protected BenchmarkExecutor() {
    this(MATH_WARMUP, MATH_ITERATIONS);
  }

  protected double runPrecompileBenchmark(final Bytes arg, final PrecompiledContract contract) {
    if (contract.computePrecompile(arg, fakeFrame).getOutput() == null) {
      throw new RuntimeException("Input is Invalid");
    }

    for (int i = 0; i < warmup; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }
    final Stopwatch timer = Stopwatch.createStarted();
    for (int i = 0; i < iterations; i++) {
      contract.computePrecompile(arg, fakeFrame);
    }
    timer.stop();

    final double elapsed = timer.elapsed(TimeUnit.NANOSECONDS) / 1.0e9D;
    return elapsed / iterations;
  }

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

  public abstract void runBenchmark(
      final PrintStream output, final Boolean attemptNative, final String fork);
}
