package org.hyperledger.besu.evmtool.benchmarks;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

/** Benchmark RIPEMD160 precompile */
public class RipeMD160Benchmark extends BenchmarkExecutor {
  /**
   * The constructor. Use default math based warmup and interations.
   *
   * @param output where to write the stats.
   * @param benchmarkConfig benchmark configurations.
   */
  public RipeMD160Benchmark(final PrintStream output, final BenchmarkConfig benchmarkConfig) {
    super(MATH_WARMUP, MATH_ITERATIONS, output, benchmarkConfig);
  }

  @Override
  public void runBenchmark(final Boolean attemptNative, final String fork) {
    EvmSpecVersion forkVersion = EvmSpecVersion.fromName(fork);

    if (attemptNative != null && attemptNative) {
      output.println("Native is unsupported, falling back to Java");
    }
    output.println("Java RIPEMD160");

    PrecompiledContract contract =
      EvmSpec.evmSpec(forkVersion).getPrecompileContractRegistry().get(Address.RIPEMD160);

    final Map<String, Bytes> testCases = new LinkedHashMap<>();
    final Random random = new Random();
    for (int len = 0; len <= 256; len += 16) {
      final byte[] data = new byte[len];
      random.nextBytes(data);
      testCases.put("size=" + len, Bytes.wrap(data));
    }
    precompile(testCases, contract, forkVersion);
  }

  @Override
  public boolean isPrecompile() {
    return true;
  }
}
