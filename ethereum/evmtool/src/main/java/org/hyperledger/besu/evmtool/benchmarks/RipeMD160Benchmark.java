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
package org.hyperledger.besu.evmtool.benchmarks;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.fluent.EvmSpec;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.SequencedMap;

import org.apache.tuweni.bytes.Bytes;

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

    final SequencedMap<String, Bytes> testCases = new LinkedHashMap<>();
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
