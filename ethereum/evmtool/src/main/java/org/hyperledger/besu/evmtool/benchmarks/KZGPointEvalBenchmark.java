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
import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;

/** Benchmark KZGPointEval precompile (SHA-256 of commitmment + KZG verify proof) */
public class KZGPointEvalBenchmark extends BenchmarkExecutor {
  /**
   * The constructor. Use default math based warmup and interations.
   *
   * @param output print stream to print the output to.
   * @param benchmarkConfig options to give to the benchmark runner.
   */
  public KZGPointEvalBenchmark(final PrintStream output, final BenchmarkConfig benchmarkConfig) {
    super(MATH_WARMUP, MATH_ITERATIONS, output, benchmarkConfig);
  }

  @Override
  public void runBenchmark(final Boolean attemptNative, final String fork) {
    EvmSpecVersion forkVersion = EvmSpecVersion.fromName(fork);

    if (attemptNative == null || !attemptNative) {
      output.println("Java is unsupported, falling back to Native");
    }
    output.println("Native KZGPointEval");

    final Map<String, Bytes> testCases = new LinkedHashMap<>();
    testCases.put(
        "kzg-verify",
        Bytes.fromHexString(
            "010657f37554c781402a22917dee2f75def7ab966d7b770905398eba3c444014623ce31cf9759a5c8daf3a357992f9f3dd7f9339d8998bc8e68373e54f00b75e0000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

    PrecompiledContract contract =
        EvmSpec.evmSpec(forkVersion).getPrecompileContractRegistry().get(Address.KZG_POINT_EVAL);

    KZGPointEvalPrecompiledContract.init();
    precompile(testCases, contract, forkVersion);
  }

  @Override
  public boolean isPrecompile() {
    return true;
  }
}
